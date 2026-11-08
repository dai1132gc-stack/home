package com.example.realestate.modules.contract.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 版本化合同模板：合同创建时把模板正文写入快照，模板后续修改不影响历史合同。
 */
@Data
@NoArgsConstructor
@TableName("contract_template")
public class ContractTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;

    private String name;

    private String version;

    private String content;

    private Integer active;

    private Instant createdAt;
}
