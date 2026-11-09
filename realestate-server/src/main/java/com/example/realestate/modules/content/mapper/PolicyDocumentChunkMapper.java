package com.example.realestate.modules.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.realestate.modules.content.model.entity.PolicyDocumentChunk;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface PolicyDocumentChunkMapper extends BaseMapper<PolicyDocumentChunk> {

    /** 召回候选：已发布政策且已生成向量的分块（含政策标题/文号/来源用于引用） */
    @Select("SELECT c.id, c.policy_id, c.chunk_index, c.content, c.embedding, c.embedding_model, " +
            "p.title AS policy_title, p.doc_no AS policy_doc_no, p.source AS policy_source " +
            "FROM policy_document_chunk c JOIN policy_document p ON p.id = c.policy_id " +
            "WHERE p.deleted = 0 AND p.status = 'PUBLISHED' AND c.embedding IS NOT NULL")
    List<IndexedChunkRow> selectIndexedChunks();

    @Select("SELECT COUNT(*) FROM policy_document_chunk WHERE embedding IS NOT NULL")
    long countIndexed();

    record IndexedChunkRow(Long id, Long policyId, Integer chunkIndex, String content, String embedding,
                           String embeddingModel, String policyTitle, String policyDocNo, String policySource) {
    }
}
