package com.example.realestate.modules.contract.service.impl;

import com.example.realestate.infrastructure.lock.RedisDistributedLock;
import com.example.realestate.modules.contract.mapper.ContractMapper;
import com.example.realestate.modules.contract.mapper.ContractStatusLogMapper;
import com.example.realestate.modules.contract.model.entity.Contract;
import com.example.realestate.modules.contract.model.entity.ContractStatusLog;
import com.example.realestate.modules.contract.service.ContractExpiryService;
import com.example.realestate.modules.house.service.HouseLifecycleService;
import com.example.realestate.modules.transaction.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("合同到期任务：Redis 锁、幂等与房源释放")
class ContractExpiryServiceImplTest {

    @Mock
    private ContractMapper contractMapper;
    @Mock
    private ContractStatusLogMapper statusLogMapper;
    @Mock
    private TransactionService transactionService;
    @Mock
    private HouseLifecycleService houseLifecycle;
    @Mock
    private RedisDistributedLock lock;
    @Mock
    private TransactionTemplate transactionTemplate;

    private ContractExpiryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ContractExpiryServiceImpl(contractMapper, statusLogMapper, transactionService,
                houseLifecycle, lock, transactionTemplate);
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
    }

    private Contract contract(Long id, Long houseId) {
        Contract entity = new Contract();
        entity.setId(id);
        entity.setHouseId(houseId);
        entity.setStatus("ACTIVE");
        entity.setEndDate(LocalDate.of(2026, 2, 1));
        return entity;
    }

    @Test
    @DisplayName("未获取到 Redis 锁时跳过，不访问数据库")
    void skipWhenLocked() {
        when(lock.tryLock(any(), any())).thenReturn(null);

        ContractExpiryService.ExpiryResult result = service.expireOverdueContracts();

        assertThat(result.skippedByLock()).isTrue();
        assertThat(result.expiredCount()).isZero();
        verify(contractMapper, never()).selectExpiredCandidates(any(), anyInt());
    }

    @Test
    @DisplayName("到期合同置 EXPIRED、写日志、取消节点并释放房源")
    void expireOneContract() {
        when(lock.tryLock(any(), any())).thenReturn("token");
        Contract contract = contract(10L, 7L);
        when(contractMapper.selectExpiredCandidates(any(), anyInt()))
                .thenReturn(List.of(contract))
                .thenReturn(List.of());
        when(contractMapper.expireIfActive(eq(10L), any(), any())).thenReturn(1);
        when(contractMapper.countOccupyingByHouse(7L, 10L)).thenReturn(0L);

        ContractExpiryService.ExpiryResult result = service.expireOverdueContracts();

        assertThat(result.expiredCount()).isEqualTo(1);
        assertThat(result.skippedByLock()).isFalse();

        ArgumentCaptor<ContractStatusLog> captor = ArgumentCaptor.forClass(ContractStatusLog.class);
        verify(statusLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getFromStatus()).isEqualTo("ACTIVE");
        assertThat(captor.getValue().getToStatus()).isEqualTo("EXPIRED");
        assertThat(captor.getValue().getOperatorId()).isNull();
        verify(transactionService).cancelPending(eq(10L), any());
        verify(houseLifecycle).releaseContract(7L);
        verify(lock).unlock(eq("lock:contract:expiry"), eq("token"));
    }

    @Test
    @DisplayName("同一房源仍有其他占用合同时不释放房源")
    void keepsHouseWhenOtherContractOccupies() {
        when(lock.tryLock(any(), any())).thenReturn("token");
        when(contractMapper.selectExpiredCandidates(any(), anyInt()))
                .thenReturn(List.of(contract(11L, 8L)))
                .thenReturn(List.of());
        when(contractMapper.expireIfActive(eq(11L), any(), any())).thenReturn(1);
        when(contractMapper.countOccupyingByHouse(8L, 11L)).thenReturn(1L);

        service.expireOverdueContracts();

        verify(houseLifecycle, never()).releaseContract(anyLong());
    }

    @Test
    @DisplayName("条件更新 0 行视为已处理，不重复写日志（幂等）")
    void idempotentWhenAlreadyExpired() {
        when(lock.tryLock(any(), any())).thenReturn("token");
        when(contractMapper.selectExpiredCandidates(any(), anyInt()))
                .thenReturn(List.of(contract(12L, 9L)))
                .thenReturn(List.of());
        when(contractMapper.expireIfActive(eq(12L), any(), any())).thenReturn(0);

        ContractExpiryService.ExpiryResult result = service.expireOverdueContracts();

        assertThat(result.expiredCount()).isZero();
        verify(statusLogMapper, never()).insert(any(ContractStatusLog.class));
        verify(transactionService, never()).cancelPending(anyLong(), any());
    }
}
