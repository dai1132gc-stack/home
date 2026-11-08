package com.example.realestate.modules.contract.service;

import java.time.Instant;

/**
 * 合同到期任务：把 end_date 已过的 ACTIVE 合同置 EXPIRED，写状态日志、取消未完成交易节点，
 * 无其他占用合同时释放房源（CONTRACTED → PUBLISHED）。
 * 任务幂等 + Redis 锁，重复执行不改变已 EXPIRED 合同。
 */
public interface ContractExpiryService {

    ExpiryResult expireOverdueContracts();

    record ExpiryResult(int expiredCount, Instant executedAt, boolean skippedByLock) {
    }
}
