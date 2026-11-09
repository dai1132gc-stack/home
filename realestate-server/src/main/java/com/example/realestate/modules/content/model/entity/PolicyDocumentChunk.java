package com.example.realestate.modules.content.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/** 政策文档分块（V4 建表，V6 增列 embedding/embedding_model/updated_at）。 */
@Data
@TableName("policy_document_chunk")
public class PolicyDocumentChunk {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long policyId;
    private Integer chunkIndex;
    private String content;
    /** 历史预留字段（V4），保留不删 */
    private String embeddingRef;
    /** 向量 JSON 数组（W6） */
    private String embedding;
    private String embeddingModel;
    private Instant createdAt;
    private Instant updatedAt;
}
