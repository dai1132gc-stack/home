package com.example.realestate.modules.content.model.vo;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Mapper 查询行（可变 POJO，便于按列名映射），Service 转为不可变 VO。 */
@Data
@NoArgsConstructor
public class GuideListRow {

    private Long id;

    private String title;

    private String category;

    private String summary;

    private String status;

    private Integer sort;

    private Integer version;

    private Instant createdAt;

    private Instant updatedAt;
}
