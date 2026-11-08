package com.example.realestate.modules.transaction.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 合同交易节点：DEPOSIT/SIGN/HANDOVER，每个合同每节点唯一。
 */
@Data
@NoArgsConstructor
@TableName("transaction_process")
public class TransactionProcess {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long contractId;

    private String node;

    private String status;

    private Instant doneAt;

    private String remark;

    private Instant createdAt;

    private Instant updatedAt;
}
