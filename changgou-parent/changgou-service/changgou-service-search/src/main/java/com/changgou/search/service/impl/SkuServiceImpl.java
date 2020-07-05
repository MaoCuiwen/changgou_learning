package com.changgou.search.service.impl;

import com.alibaba.fastjson.JSON;
import com.changgou.goods.feign.SkuFeign;
import com.changgou.goods.pojo.Sku;
import com.changgou.search.dao.SkuEsMapper;
import com.changgou.search.pojo.SkuInfo;
import com.changgou.search.service.SkuService;
import entity.Result;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.StringTerms;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.aggregation.AggregatedPage;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

        //构建查询条件
        if (map != null && map.size() > 0) {
            String keywords = map.get("keywords");
            if (!StringUtils.isEmpty(keywords)) {
                builder.withQuery(
                        QueryBuilders.matchQuery("name", keywords)
                );
            }
        }

        //根据关键字查询关联的分类信息，例如关键字小米，查询小米所属的所有分类，比如手机/笔记本/平衡车等等
        //.terms()方法的作用是起别名
        builder.addAggregation(AggregationBuilders.terms("skuCategorygroup").field("categoryName").size(50));
        //根据关键字查询关联的品牌信息，例如关键字手机，查询手机对应的品牌集合
        builder.addAggregation(AggregationBuilders.terms("skuBrandgroup").field("brandName").size(50));

        NativeSearchQuery query = builder.build();
        AggregatedPage<SkuInfo> page = elasticsearchTemplate.queryForPage(query, SkuInfo.class);

        //获取分组结果
        List<String> categoryList = getStringsCategoryList(page);
        List<String> stringsBrandList = getStringsBrandList(page);

        List<SkuInfo> skuInfos = page.getContent();
        long totalElements = page.getTotalElements();
        int totalPages = page.getTotalPages();

        HashMap<String, Object> hashMap = new HashMap<>();
        hashMap.put("totalPages", totalPages);
        hashMap.put("totalElements", totalElements);
        hashMap.put("contents", skuInfos);
        hashMap.put("categoryList", categoryList);
        hashMap.put("brandList", stringsBrandList);

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
}
