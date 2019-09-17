package com.hl.service.impl;

import com.hl.page.Page;
import com.hl.service.BaseSearchService;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.index.query.*;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.elasticsearch.core.query.SearchQuery;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class BaseSearchServiceImpl<T> implements BaseSearchService<T> {
    private Logger logger = LoggerFactory.getLogger(BaseSearchService.class);
    @Autowired
    private ElasticsearchTemplate elasticsearchTemplate;

    @Override
    public List<T> query(String keyword, Class<T> clazz) {
        SearchQuery searchQuery = new NativeSearchQueryBuilder()
                .withQuery(new QueryStringQueryBuilder(keyword))
                .withSort(SortBuilders.scoreSort().order(SortOrder.DESC))
                .build();
        return elasticsearchTemplate.queryForList(searchQuery, clazz);
    }

    /**
     * 高亮显示
     *
     * @param keyword    关键字
     * @param indexName  索引库
     * @param fieldNames 搜索的字段
     * @return List<Map < String, Object>>
     */
    @Override
    public List<Map<String, Object>> queryHit(String keyword, String indexName, String... fieldNames) {
        //构成查询条件，使用标准分词器
//        QueryBuilder matchQuery = createQueryBuilder(keyword.toLowerCase(), fieldNames);

        QueryStringQueryBuilder queryBuilder = new QueryStringQueryBuilder(keyword);
        queryBuilder.analyzer("ik_smart");
        queryBuilder.field("object_name");

        // 设置高亮,使用默认的highlighter高亮器
        HighlightBuilder highlightBuilder = createHighlightBuilder(fieldNames);

        SearchRequestBuilder searchRequestBuilder = elasticsearchTemplate.getClient()
                .prepareSearch(indexName)
                .setQuery(queryBuilder)
                .highlighter(highlightBuilder)
                .setSize(10000); // 设置一次返回的文档数量，最大值：1000;

        logger.info("searchRequestBuilder:{}", searchRequestBuilder);

        // 设置查询字段
        SearchResponse response = searchRequestBuilder.get();
        // 返回搜索结果
        SearchHits hits = response.getHits();

        return getHitList(hits);
    }

    /**
     * 范围查询
     * @param fieldNames 字段
     * @param indexName 索引库
     */
    @Override
    public List<Map<String, Object>> queryHitDate(String indexName,
            String fieldNames,String startDate, String endDate) {

        //时间范围的设定
        RangeQueryBuilder rangeQueryBuilder=QueryBuilders
                .rangeQuery(fieldNames)
                .format("yyyy-MM-dd HH:mm:ss")
                .gt(startDate)
                .lt(endDate);

        QueryBuilder qbName=QueryBuilders.termQuery("code","local");

        //select * from  user where code='local' and ( ctime>2019-05-12 10:15:14 and ctime <  2019-09-11 23:26:44 ) ;
        QueryBuilder qb=QueryBuilders.boolQuery()
                .must(qbName)
                .must(rangeQueryBuilder);


        SearchRequestBuilder searchRequestBuilder = elasticsearchTemplate.getClient()
                .prepareSearch(indexName)
//                .setTypes("_doc")
                .setQuery(qb)
                .addSort(fieldNames,SortOrder.DESC)
                .setSize(1000); // 设置一次返回的文档数量，最大值：1000;

        logger.info("searchRequestBuilder:{}", searchRequestBuilder);

        // 设置查询字段
        SearchResponse response = searchRequestBuilder.get();
        // 返回搜索结果
        SearchHits hits = response.getHits();

        return getHitList(hits);
    }

    /**
     * 高亮显示,返回分页
     *
     * @param pageNo     当前页
     * @param pageSize   每页显示的总条数
     * @param keyword    关键字
     * @param indexName  索引库
     * @param fieldNames 搜索的字段
     * @return Page<Map < String, Object>>
     */
    @Override
    public Page<Map<String, Object>> queryHitByPage(int pageNo, int pageSize, String keyword, String indexName,
            String... fieldNames) {
        //构成查询条件，使用标准分词器
        QueryBuilder matchQuery = createQueryBuilder(keyword, fieldNames);

        // 设置高亮,使用默认的highlighter高亮器
        HighlightBuilder highlightBuilder = createHighlightBuilder(fieldNames);

        // 设置查询字段
        SearchResponse searchResponse = elasticsearchTemplate.getClient().prepareSearch(indexName)
                .setQuery(matchQuery)
                .highlighter(highlightBuilder).setFrom((pageNo - 1) * pageSize)
                .setSize(pageNo * pageSize) // 设置一次返回的文档数量，最大值：10000
                .get();
        // 返回搜索结果
        SearchHits hits = searchResponse.getHits();
        Long totalCount = hits.getTotalHits();
        Page<Map<String, Object>> page = new Page<>(pageNo, pageSize, totalCount.intValue());
        page.setList(getHitList(hits));

        return page;
    }

    private QueryBuilder createQueryBuilder(String keyword, String... fieldNames) {
        // 构造查询条件,使用标准分词器
        // matchQuery(),单字段搜索
        return QueryBuilders.multiMatchQuery(keyword, fieldNames).analyzer("ik_max_word").operator(Operator.OR);
    }

    /**
     * 构造高亮器
     */
    private HighlightBuilder createHighlightBuilder(String... fieldNames) {
        // 设置高亮,使用默认的highlighter高亮器
        HighlightBuilder highlightBuilder = new HighlightBuilder()
                // .field("productName")
                .preTags("<span style='color:red'>").postTags("</span>");

        // 设置高亮字段
        for (String fieldName : fieldNames) {
            highlightBuilder.field(fieldName);
        }

        return highlightBuilder;
    }

    /**
     * 处理高亮结果
     */
    private List<Map<String, Object>> getHitList(SearchHits hits) {
        List<Map<String, Object>> list = new ArrayList<>();
        Map<String, Object> map;
        for (SearchHit searchHit : hits) {
            map = new HashMap<>();
            // 处理源数据
            map.put("source", searchHit.getSourceAsMap());
            // 处理高亮数据
            Map<String, Object> hitMap = new HashMap<>();
            searchHit.getHighlightFields().forEach((k, v) -> {
                StringBuffer hight = new StringBuffer();
                for (Text text : v.getFragments()) {
                    hight.append(text.string());
                    hitMap.put(v.getName(), hight);
                }
            });
            if (hitMap.size()>0){
                map.put("highlight", hitMap);
            }
            list.add(map);
        }
        return list;
    }

    @Override
    public void deleteIndex(String indexName) {
        elasticsearchTemplate.deleteIndex(indexName);
    }
}
