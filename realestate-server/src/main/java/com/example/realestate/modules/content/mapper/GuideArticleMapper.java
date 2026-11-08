package com.example.realestate.modules.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.realestate.modules.content.model.entity.GuideArticle;
import com.example.realestate.modules.content.model.query.ContentQuery;
import com.example.realestate.modules.content.model.vo.GuideListRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

/** 内容 Mapper：公开访问强制 status='PUBLISHED'，排序命中 (status,category,sort)。 */
@Mapper
public interface GuideArticleMapper extends BaseMapper<GuideArticle> {

    List<GuideListRow> selectPageList(@Param("q") ContentQuery query,
                                      @Param("publicOnly") boolean publicOnly,
                                      @Param("offset") long offset,
                                      @Param("size") long size);

    long countByQuery(@Param("q") ContentQuery query,
                      @Param("publicOnly") boolean publicOnly);

    /** DRAFT/OFFLINE → PUBLISHED；0 行表示状态已变化 */
    int publish(@Param("id") Long id, @Param("now") Instant now);

    /** PUBLISHED → OFFLINE；0 行表示状态已变化 */
    int offline(@Param("id") Long id, @Param("now") Instant now);
}
