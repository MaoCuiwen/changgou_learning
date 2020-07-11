package com.changgou.search.service.impl;

import com.alibaba.fastjson.JSON;
import com.changgou.goods.feign.SkuFeign;
import com.changgou.goods.pojo.Sku;
import com.changgou.search.dao.SkuEsMapper;
import com.changgou.search.pojo.SkuInfo;
import com.changgou.search.service.SkuService;
import entity.Result;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.StringTerms;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.aggregation.AggregatedPage;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;

@Service
public class SkuServiceImpl implements SkuService {
    @Autowired
    private SkuFeign skuFeign;

    @Autowired
    private SkuEsMapper skuEsMapper;

    @Autowired
    private ElasticsearchTemplate elasticsearchTemplate;

    @Override
    public void importSku() {
        //远程调用服务获取审核通过的所有sku数据(注：实际开发中应该分页获取)
        Result<List<Sku>> result = skuFeign.findByStatus("1");
        List<Sku> skus = result.getData();

        //将sku数据转换为skuinfo数据
        List<SkuInfo> skuInfos=  JSON.parseArray(JSON.toJSONString(skus),SkuInfo.class);

        for(SkuInfo skuInfo:skuInfos){
            Map<String, Object> specMap= JSON.parseObject(skuInfo.getSpec()) ;
            skuInfo.setSpecMap(specMap);
        }

        //保存到elasticsearch中
        skuEsMapper.saveAll(skuInfos);

    }

    @Override
    public Map<String, Object> search(Map<String, String> map) {
        NativeSearchQueryBuilder builder = new NativeSearchQueryBuilder();

        //构建聚合查询条件
        //根据关键字查询关联的分类信息，例如关键字小米，查询小米所属的所有分类，比如手机/笔记本/平衡车等等
        //.terms()方法的作用是起别名
        builder.addAggregation(AggregationBuilders.terms("skuCategorygroup").field("categoryName").size(50));
        //根据关键字查询关联的品牌信息，例如关键字手机，查询手机对应的品牌集合
        builder.addAggregation(AggregationBuilders.terms("skuBrandgroup").field("brandName").size(50));
        //分组条件，商品规格 .keyword表示按照关键字查询，不做分词
        builder.addAggregation(AggregationBuilders.terms("skuSpecgroup").field("spec.keyword").size(100));


        if (map != null && map.size() > 0) {

            //设置高亮条件
            builder.withHighlightFields(new HighlightBuilder.Field("name"));
            builder.withHighlightBuilder(new HighlightBuilder().preTags("<em style=\"color:red\">").postTags("</em>"));

            //构建主关键字查询条件 withQuery
            if (!StringUtils.isEmpty(map.get("keywords"))) {
//                builder.withQuery(QueryBuilders.matchQuery("name", map.get("keywords")));
                //参数1，搜索的关键词；参数2，需要匹配的域(可以多个)
                builder.withQuery(QueryBuilders.multiMatchQuery(map.get("keywords"),
                        "name","brandName","categoryName"));
            }

            //组合条件设置 withFilter
            BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
            if (!StringUtils.isEmpty(map.get("brand"))) {   //如果用户指定了品牌搜索
                boolQueryBuilder.filter(QueryBuilders.termQuery("brandName", map.get("brand")));
            }
            if (!StringUtils.isEmpty(map.get("category"))) {   //如果用户指定了分类搜索
                boolQueryBuilder.filter(QueryBuilders.termQuery("categoryName", map.get("category")));
            }
            //规格过滤查询
            for (String key : map.keySet()) {
                if (key.startsWith("spec_")) {
                    boolQueryBuilder.filter(QueryBuilders.termQuery("specMap." + key.substring(5) + ".keyword", map.get(key)));
                }
            }
            //价格过滤查询
            String price = map.get("price");
            if (!StringUtils.isEmpty(price)) {
                String[] split = price.split("-");
                if (!split[1].equalsIgnoreCase("*")) {   //100-200格式
                    boolQueryBuilder.filter(QueryBuilders.rangeQuery("price").from(split[0], true).to(split[1], true));
                } else {  //100-*格式
                    boolQueryBuilder.filter(QueryBuilders.rangeQuery("price").gte(split[0]));
                }
            }
            builder.withFilter(boolQueryBuilder);

            //构建分页查询 withPageable
            Integer pageNum = 1;
            if (!StringUtils.isEmpty(map.get("pageNum"))) {
                try {
                    pageNum = Integer.valueOf(map.get("pageNum"));
                    if (pageNum < 1)
                        pageNum = 1;
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                    pageNum=1;
                }
            }
            Integer pageSize = 3;
            builder.withPageable(PageRequest.of(pageNum - 1, pageSize));


            //构建排序查询 withSort
            String sortRule = map.get("sortRule");
            String sortField = map.get("sortField");
            if (!StringUtils.isEmpty(sortRule) && !StringUtils.isEmpty(sortField)) {
                builder.withSort(SortBuilders.fieldSort(sortField).order(sortRule.equals("DESC") ? SortOrder.DESC : SortOrder.ASC));
            }
        }

        //条件构建完毕，开始搜索
        NativeSearchQuery query = builder.build();
        AggregatedPage<SkuInfo> page = elasticsearchTemplate.queryForPage(query, SkuInfo.class, new SearchResultMapperImpl());

        //获取分组结果
        List<String> categoryList = getStringsCategoryList(page);
        List<String> stringsBrandList = getStringsBrandList(page);
        Map<String, Set<String>> stringSetMap = getStringSetMap(page);

        List<SkuInfo> skuInfos = page.getContent();
        long totalElements = page.getTotalElements();
        int totalPages = page.getTotalPages();

        HashMap<String, Object> hashMap = new HashMap<>();
        hashMap.put("totalPages", totalPages);
        hashMap.put("totalElements", totalElements);
        hashMap.put("rows", skuInfos);
        hashMap.put("categoryList", categoryList);
        hashMap.put("brandList", stringsBrandList);
        hashMap.put("specMap", stringSetMap);

        return hashMap;
    }

    private List<String> getStringsBrandList(AggregatedPage<SkuInfo> page) {
        StringTerms stringTerms = (StringTerms)page.getAggregation("skuBrandgroup");
        List<String> brandList = new ArrayList<>();
        if (stringTerms != null) {
            for (StringTerms.Bucket bucket : stringTerms.getBuckets()) {
                String keyAsString = bucket.getKeyAsString();//每个分类分组的值
                brandList.add(keyAsString);
            }
        }
        return brandList;
    }

    private List<String> getStringsCategoryList(AggregatedPage<SkuInfo> page) {
        StringTerms stringTerms = (StringTerms)page.getAggregation("skuCategorygroup");
        List<String> categoryList = new ArrayList<>();
        if (stringTerms != null) {
            for (StringTerms.Bucket bucket : stringTerms.getBuckets()) {
                String keyAsString = bucket.getKeyAsString();//每个分类分组的值
                categoryList.add(keyAsString);
            }
        }
        return categoryList;
    }

    /**
     * 获取规格列表数据
     *
     * @return
     */
    private Map<String, Set<String>> getStringSetMap(AggregatedPage<SkuInfo> page) {
        StringTerms stringTermsSpec = (StringTerms)page.getAggregation("skuSpecgroup");

        Map<String, Set<String>> specMap = new HashMap<String, Set<String>>();
        Set<String> specList = new HashSet<>();
        if (stringTermsSpec != null) {
            for (StringTerms.Bucket bucket : stringTermsSpec.getBuckets()) {
                specList.add(bucket.getKeyAsString());
            }
        }
        for (String specjson : specList) {
            //将每条规格(string)转换成map
            Map<String, String> map = JSON.parseObject(specjson, Map.class);
            for (Map.Entry<String, String> entry : map.entrySet()) {//
                String key = entry.getKey();        //规格名字
                String value = entry.getValue();    //规格选项值
                //获取当前规格名字对应的规格数据
                Set<String> specValues = specMap.get(key);
                if (specValues == null) {
                    specValues = new HashSet<String>();
                }
                //将当前规格加入到集合中 set保证不会插入重复的规格值
                specValues.add(value);
                //将数据存入到specMap中
                specMap.put(key, specValues);
            }
        }
        return specMap;
    }
}
