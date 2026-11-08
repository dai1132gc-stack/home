package com.example.realestate.modules.owner.model.vo;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Mapper 查询行（可变 POJO），Service 补齐跟进方式标签后转 VO。 */
@Data
@NoArgsConstructor
public class CustomerFollowRow {

    private Long id;

    private Long ownerId;

    private String content;

    private String followType;

    private Instant nextFollowAt;

    private Long createdBy;

    private String createdByName;

    private Instant createdAt;
}
