package com.example.realestate.modules.transaction.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 交易节点状态日志（谁在何时把哪个节点从什么状态改成什么状态）。
 */
@Data
@NoArgsConstructor
@TableName("transaction_status_log")
public class TransactionStatusLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long contractId;

    private String node;

    private String fromStatus;

    private String toStatus;

    private Long operatorId;

    private String reason;

    private Instant createdAt;
}
