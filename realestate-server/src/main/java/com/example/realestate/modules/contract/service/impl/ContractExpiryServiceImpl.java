package com.example.realestate.modules.contract.service.impl;

import com.example.realestate.common.enums.ContractStatus;
import com.example.realestate.infrastructure.lock.RedisDistributedLock;
import com.example.realestate.modules.contract.mapper.ContractMapper;
import com.example.realestate.modules.contract.mapper.ContractStatusLogMapper;
import com.example.realestate.modules.contract.model.entity.Contract;
import com.example.realestate.modules.contract.model.entity.ContractStatusLog;
import com.example.realestate.modules.contract.service.ContractExpiryService;
import com.example.realestate.modules.house.service.HouseLifecycleService;
import com.example.realestate.modules.transaction.service.TransactionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * 到期任务实现：
 *  - 单实例假设 + Redis 锁（lock:contract:expiry，TTL 10 分钟）防止定时与手动触发叠加；
 *  - 逐条条件 UPDATE（status='ACTIVE' AND end_date < today），重复执行幂等；
 *  - 每条到期合同在独立事务中处理：写日志、取消未完成节点、无其他占用合同时释放房源。
 */
@Service
public class ContractExpiryServiceImpl implements ContractExpiryService {

    private static final Logger log = LoggerFactory.getLogger(ContractExpiryServiceImpl.class);
    private static final String LOCK_KEY = "lock:contract:expiry";
    private static final Duration LOCK_TTL = Duration.ofMinutes(10);
    private static final int BATCH_SIZE = 200;
    private static final int MAX_BATCHES = 50;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final String REASON = "租期届满，系统自动置为已到期";

    private final ContractMapper contractMapper;
    private final ContractStatusLogMapper statusLogMapper;
    private final TransactionService transactionService;
    private final HouseLifecycleService houseLifecycle;
    private final RedisDistributedLock lock;
    private final TransactionTemplate transactionTemplate;

    public ContractExpiryServiceImpl(ContractMapper contractMapper,
                                     ContractStatusLogMapper statusLogMapper,
                                     TransactionService transactionService,
                                     HouseLifecycleService houseLifecycle,
                                     RedisDistributedLock lock,
                                     TransactionTemplate transactionTemplate) {
        this.contractMapper = contractMapper;
        this.statusLogMapper = statusLogMapper;
        this.transactionService = transactionService;
        this.houseLifecycle = houseLifecycle;
        this.lock = lock;
        this.transactionTemplate = transactionTemplate;
    }

    /** 每日 01:10（Asia/Shanghai）执行；可通过 app.contract.expiry-cron 覆盖 */
    @Scheduled(cron = "${app.contract.expiry-cron:0 10 1 * * *}", zone = "Asia/Shanghai")
    public void scheduledExpiry() {
        ExpiryResult result = expireOverdueContracts();
        if (!result.skippedByLock()) {
            log.info("合同到期定时任务完成 expired={}", result.expiredCount());
        }
    }

    @Override
    public ExpiryResult expireOverdueContracts() {
        String token = lock.tryLock(LOCK_KEY, LOCK_TTL);
        if (token == null) {
            log.warn("合同到期任务已在执行，本次跳过");
            return new ExpiryResult(0, Instant.now(), true);
        }
        try {
            int total = 0;
            for (int batch = 0; batch < MAX_BATCHES; batch++) {
                List<Contract> candidates = contractMapper.selectExpiredCandidates(today(), BATCH_SIZE);
                if (candidates.isEmpty()) {
                    break;
                }
                for (Contract contract : candidates) {
                    Boolean changed = transactionTemplate.execute(status -> expireOne(contract));
                    if (Boolean.TRUE.equals(changed)) {
                        total++;
                    }
                }
            }
            return new ExpiryResult(total, Instant.now(), false);
        } finally {
            lock.unlock(LOCK_KEY, token);
        }
    }

    private boolean expireOne(Contract contract) {
        Instant now = Instant.now();
        int rows = contractMapper.expireIfActive(contract.getId(), today(), now);
        if (rows == 0) {
            // 已被其他实例/人工处理，保持幂等
            return false;
        }
        ContractStatusLog statusLog = new ContractStatusLog();
        statusLog.setContractId(contract.getId());
        statusLog.setFromStatus(ContractStatus.ACTIVE.name());
        statusLog.setToStatus(ContractStatus.EXPIRED.name());
        statusLog.setOperatorId(null);
        statusLog.setReason(REASON);
        statusLog.setCreatedAt(now);
        statusLogMapper.insert(statusLog);

        transactionService.cancelPending(contract.getId(), REASON);
        if (contractMapper.countOccupyingByHouse(contract.getHouseId(), contract.getId()) == 0) {
            houseLifecycle.releaseContract(contract.getHouseId());
        }
        log.info("合同到期 id={} houseId={} endDate={}", contract.getId(), contract.getHouseId(),
                contract.getEndDate());
        return true;
    }

    private LocalDate today() {
        return LocalDate.now(ZONE);
    }
}
