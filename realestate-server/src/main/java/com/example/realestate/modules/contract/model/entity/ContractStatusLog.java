package com.example.realestate.modules.contract.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 合同状态变更日志：fromStatus 为空表示创建。
 */
@Data
@NoArgsConstructor
@TableName("contract_status_log")
public class ContractStatusLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long contractId;

    private String fromStatus;

    private String toStatus;

    private Long operatorId;

    private String reason;

    private Instant createdAt;
}
