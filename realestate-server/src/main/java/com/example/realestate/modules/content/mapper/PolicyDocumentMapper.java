package com.example.realestate.modules.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.realestate.modules.content.model.entity.PolicyDocument;
import com.example.realestate.modules.content.model.query.ContentQuery;
import com.example.realestate.modules.content.model.vo.PolicyListRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

/** 政策 Mapper：公开访问强制 status='PUBLISHED'，排序命中 (status,doc_type,publish_date)。 */
@Mapper
public interface PolicyDocumentMapper extends BaseMapper<PolicyDocument> {

    List<PolicyListRow> selectPageList(@Param("q") ContentQuery query,
                                       @Param("publicOnly") boolean publicOnly,
                                       @Param("offset") long offset,
                                       @Param("size") long size);

    long countByQuery(@Param("q") ContentQuery query,
                      @Param("publicOnly") boolean publicOnly);

    int publish(@Param("id") Long id, @Param("now") Instant now);

    int offline(@Param("id") Long id, @Param("now") Instant now);

    int updateAttachment(@Param("id") Long id,
                         @Param("attachmentKey") String attachmentKey,
                         @Param("now") Instant now);
}
