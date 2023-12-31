package com.hl.service;

import com.hl.page.Page;

import java.util.List;
import java.util.Map;

public interface BaseSearchService<T> {
    /**
     * 搜 索
     */
    List<T> query(String keyword, Class<T> clazz);

    /**
     * 搜索高亮显示
     *
     * @param keyword    关键字
     * @param indexName  索引库
     * @param fieldNames 搜索的字段
     */
    List<Map<String, Object>> queryHit(String keyword, String indexName, String... fieldNames);

    /**
     * 时间范围
     *
     * @param fieldNames 字段
     * @param indexName  索引库
     */
    List<Map<String, Object>> queryHitDate(String indexName, String fieldNames, String startDate, String endDate);

    /**
     * 搜索高亮显示，返回分页
     *
     * @param pageNo     当前页
     * @param pageSize   每页显示的总条数
     * @param keyword    关键字
     * @param indexName  索引库
     * @param fieldNames 搜索的字段
     */
    Page<Map<String, Object>> queryHitByPage(int pageNo, int pageSize, String keyword, String indexName,
            String... fieldNames);

    /**
     * elasticsearchTemplate 实现 searchafter
     */
    Page<Map<String, Object>> searchAfterByPage(int pageNo, int pageSize, String keyword, String indexName,
            String fieldNames);

    /**
     * RestHighLevelClient 实现search_after
     */
    Page<Map<String, Object>> searchAfterByPageRestHighLevel(int pageNo, int pageSize, String keyword, String indexName,
            String fieldNames);

    /**
     * 删除索引库
     */
    void deleteIndex(String indexName);
}
