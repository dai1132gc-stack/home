package com.example.realestate.modules.content.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

/** 政策文档：公开只读已发布；附件为私有存储相对 key。 */
@Data
@NoArgsConstructor
@TableName("policy_document")
public class PolicyDocument {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String docNo;

    private String docType;

    private String source;

    private LocalDate publishDate;

    private LocalDate effectiveDate;

    private String content;

    private String attachmentKey;

    private String status;

    @Version
    private Integer version;

    @TableLogic
    private Integer deleted;

    private Instant createdAt;

    private Instant updatedAt;
}
