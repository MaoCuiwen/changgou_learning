package com.changgou.search.service;

import java.util.Map;

public interface SkuService {
    /**
     * 从MySQL数据库中导入数据到elasticsearch
     */
    void importSku();

    /**
     * 指定条件在elasticsearch中搜索
     * @param map 搜索条件
     * @return
     */
    Map<String, Object> search(Map<String, String> map);
}
