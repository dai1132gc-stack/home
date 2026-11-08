package com.example.realestate.modules.content.model.vo;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

/** Mapper 查询行（可变 POJO，便于按列名映射），Service 转为不可变 VO。 */
@Data
@NoArgsConstructor
public class PolicyListRow {

    private Long id;

    private String title;

    private String docNo;

    private String docType;

    private String source;

    private LocalDate publishDate;

    private LocalDate effectiveDate;

    private String status;

    private Integer version;

    private Instant createdAt;

    private Instant updatedAt;

    private Boolean hasAttachment;
}
