package com.example.realestate.modules.contract.service.impl;

import com.example.realestate.common.enums.ErrorCode;
import com.example.realestate.common.exception.BusinessException;
import com.example.realestate.infrastructure.pdf.ContractPdfGenerator;
import com.example.realestate.modules.appointment.mapper.ViewingAppointmentMapper;
import com.example.realestate.modules.contract.convert.ContractConvert;
import com.example.realestate.modules.contract.mapper.ContractMapper;
import com.example.realestate.modules.contract.mapper.ContractPartyMapper;
import com.example.realestate.modules.contract.mapper.ContractStatusLogMapper;
import com.example.realestate.modules.contract.mapper.ContractTemplateMapper;
import com.example.realestate.modules.contract.model.dto.ContractCreateRequest;
import com.example.realestate.modules.contract.model.dto.ContractTerminateRequest;
import com.example.realestate.modules.contract.model.dto.ContractUpdateRequest;
import com.example.realestate.modules.contract.model.entity.Contract;
import com.example.realestate.modules.contract.model.entity.ContractParty;
import com.example.realestate.modules.contract.model.vo.ContractDetailVO;
import com.example.realestate.modules.file.service.PrivateStorageService;
import com.example.realestate.modules.house.mapper.HouseMapper;
import com.example.realestate.modules.house.model.entity.House;
import com.example.realestate.modules.house.service.HouseLifecycleService;
import com.example.realestate.modules.owner.model.entity.OwnerProfile;
import com.example.realestate.modules.owner.service.OwnerService;
import com.example.realestate.modules.transaction.service.TransactionService;
import com.example.realestate.modules.user.mapper.SysUserMapper;
import com.example.realestate.modules.user.model.entity.SysUser;
import com.example.realestate.security.CurrentUserProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("合同服务：快照、状态机、PDF 与房源联动")
class ContractServiceImplTest {

    @Mock
    private ContractMapper contractMapper;
    @Mock
    private ContractPartyMapper partyMapper;
    @Mock
    private ContractStatusLogMapper statusLogMapper;
    @Mock
    private ContractTemplateMapper templateMapper;
    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private ViewingAppointmentMapper appointmentMapper;
    @Mock
    private HouseMapper houseMapper;
    @Mock
    private OwnerService ownerService;
    @Mock
    private HouseLifecycleService houseLifecycle;
    @Mock
    private TransactionService transactionService;
    @Mock
    private ContractPdfGenerator pdfGenerator;
    @Mock
    private PrivateStorageService privateStorage;
    @Mock
    private CurrentUserProvider currentUser;

    private ContractServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ContractServiceImpl(contractMapper, partyMapper, statusLogMapper, templateMapper,
                sysUserMapper, appointmentMapper, houseMapper, ownerService, houseLifecycle,
                transactionService, pdfGenerator, privateStorage, new ContractConvert(),
                new ObjectMapper().findAndRegisterModules(), currentUser);
    }

    private House house(String status) {
        House house = new House();
        house.setId(7L);
        house.setTitle("金桥苑 1201");
        house.setHouseType("RENT");
        house.setStatus(status);
        house.setCommunity("金桥苑");
        house.setRegion("浦东新区");
        house.setAddress("上海市浦东新区金桥路 100 弄 5 号 1201 室");
        house.setArea(new BigDecimal("92.00"));
        house.setRooms(2);
        house.setBathrooms(1);
        house.setFloor("12/20");
        house.setOwnerId(1L);
        house.setVersion(0);
        return house;
    }

    private SysUser customer() {
        SysUser user = new SysUser();
        user.setId(2L);
        user.setUsername("customer");
        user.setNickname("演示客户");
        user.setRole(SysUser.ROLE_CUSTOMER);
        user.setStatus(SysUser.STATUS_ACTIVE);
        user.setPhone("13912345678");
        return user;
    }

    private OwnerProfile owner() {
        OwnerProfile owner = new OwnerProfile();
        owner.setId(1L);
        owner.setName("王建国");
        owner.setPhone("13800138001");
        return owner;
    }

    private Contract contract(Long id, String status, String pdfUrl) {
        Contract contract = new Contract();
        contract.setId(id);
        contract.setContractNo("HT20261108-000001");
        contract.setTitle("金桥苑 1201 租赁合同");
        contract.setHouseId(7L);
        contract.setCustomerId(2L);
        contract.setOwnerId(1L);
        contract.setAmount(new BigDecimal("5200.00"));
        contract.setDeposit(new BigDecimal("10400.00"));
        contract.setPaymentCycle("MONTHLY");
        contract.setStartDate(LocalDate.of(2026, 12, 1));
        contract.setEndDate(LocalDate.of(2027, 11, 30));
        contract.setStatus(status);
        contract.setTemplateVersion("STANDARD_LEASE-v1");
        contract.setSnapshotJson("{}");
        contract.setPdfUrl(pdfUrl);
        contract.setVersion(0);
        contract.setCreatedAt(Instant.parse("2026-11-08T00:00:00Z"));
        contract.setUpdatedAt(Instant.parse("2026-11-08T00:00:00Z"));
        return contract;
    }

    private ContractCreateRequest createRequest() {
        return new ContractCreateRequest(7L, 2L, "金桥苑 1201 租赁合同",
                new BigDecimal("5200.00"), new BigDecimal("10400.00"),
                com.example.realestate.common.enums.PaymentCycle.MONTHLY,
                LocalDate.of(2026, 12, 1), LocalDate.of(2027, 11, 30),
                null, null, null, null, "310101199001011234", "310105199203045678");
    }

    private void stubDetail(Contract stored) {
        when(contractMapper.selectVoById(anyLong())).thenReturn(null);
        when(partyMapper.selectList(any())).thenReturn(List.of());
        when(transactionService.listByContract(anyLong())).thenReturn(List.of());
        when(statusLogMapper.selectList(any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("起草合同：房源未发布返回 409")
    void createRejectsUnpublishedHouse() {
        when(houseMapper.selectById(7L)).thenReturn(house("OFFLINE"));

        assertThatThrownBy(() -> service.create(createRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    @DisplayName("起草合同：房源已有占用合同返回 409")
    void createRejectsOccupiedHouse() {
        when(houseMapper.selectById(7L)).thenReturn(house("PUBLISHED"));
        when(contractMapper.countOccupyingByHouse(7L, null)).thenReturn(1L);

        assertThatThrownBy(() -> service.create(createRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.DUPLICATE);
    }

    @Test
    @DisplayName("起草合同：客户不存在返回 404")
    void createRejectsUnknownCustomer() {
        when(houseMapper.selectById(7L)).thenReturn(house("PUBLISHED"));
        when(contractMapper.countOccupyingByHouse(7L, null)).thenReturn(0L);
        when(sysUserMapper.selectById(2L)).thenReturn(null);

        assertThatThrownBy(() -> service.create(createRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("起草合同：写入快照、签署方、交易节点并生成 PDF")
    void createPersistsSnapshotAndParties() {
        when(houseMapper.selectById(7L)).thenReturn(house("PUBLISHED"));
        when(contractMapper.countOccupyingByHouse(7L, null)).thenReturn(0L);
        when(sysUserMapper.selectById(2L)).thenReturn(customer());
        when(ownerService.findEntityOrNull(1L)).thenReturn(owner());
        when(templateMapper.selectList(any())).thenReturn(List.of());
        when(currentUser.currentUserIdOrNull()).thenReturn(1L);
        when(pdfGenerator.generate(any(), anyString(), anyString())).thenReturn("%PDF".getBytes());
        when(privateStorage.storePdf(any())).thenReturn("contract/202611/a.pdf");
        AtomicReference<Contract> inserted = new AtomicReference<>();
        when(contractMapper.insert(any(Contract.class))).thenAnswer(invocation -> {
            Contract entity = invocation.getArgument(0);
            entity.setId(5L);
            inserted.set(entity);
            return 1;
        });
        when(contractMapper.selectById(5L)).thenAnswer(invocation -> {
            Contract entity = inserted.get();
            entity.setPdfUrl("contract/202611/a.pdf");
            return entity;
        });
        stubDetail(inserted.get());

        ContractDetailVO detail = service.create(createRequest());

        assertThat(detail).isNotNull();
        assertThat(inserted.get().getStatus()).isEqualTo("DRAFT");
        assertThat(inserted.get().getSnapshotJson()).contains("金桥苑").contains("王建国");
        verify(partyMapper, org.mockito.Mockito.times(3)).insert(any(ContractParty.class));
        verify(transactionService).initNodes(eq(5L), any());
        verify(pdfGenerator).generate(any(), eq(inserted.get().getContractNo()), anyString());
    }

    @Test
    @DisplayName("提交待签：仅草稿可提交")
    void submitRequiresDraft() {
        when(contractMapper.selectById(5L)).thenReturn(contract(5L, "ACTIVE", "contract/a.pdf"));

        assertThatThrownBy(() -> service.submit(5L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    @DisplayName("提交待签：已有占用合同返回 409")
    void submitRejectsOccupied() {
        when(contractMapper.selectById(5L)).thenReturn(contract(5L, "DRAFT", null));
        when(contractMapper.countOccupyingByHouse(7L, 5L)).thenReturn(1L);

        assertThatThrownBy(() -> service.submit(5L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.DUPLICATE);
    }

    @Test
    @DisplayName("提交待签：状态迁移成功写日志")
    void submitTransitions() {
        Contract stored = contract(5L, "DRAFT", "contract/a.pdf");
        when(contractMapper.selectById(5L)).thenReturn(stored);
        when(privateStorage.exists("contract/a.pdf")).thenReturn(true);
        when(contractMapper.updateStatusIfMatch(eq(5L), eq("DRAFT"), eq("PENDING_SIGN"), any())).thenReturn(1);
        stubDetail(stored);

        service.submit(5L);

        verify(statusLogMapper).insert(any(com.example.realestate.modules.contract.model.entity.ContractStatusLog.class));
    }

    @Test
    @DisplayName("F5：事务回滚时删除本次新生成的 PDF 文件")
    void rollbackDeletesNewlyGeneratedPdf() {
        Contract stored = contract(5L, "DRAFT", null);
        when(contractMapper.selectById(5L)).thenReturn(stored);
        when(contractMapper.countOccupyingByHouse(7L, 5L)).thenReturn(0L);
        when(pdfGenerator.generate(any(), anyString(), anyString())).thenReturn("%PDF".getBytes());
        when(privateStorage.storePdf(any())).thenReturn("contract/202611/new.pdf");
        when(contractMapper.updateStatusIfMatch(eq(5L), eq("DRAFT"), eq("PENDING_SIGN"), any())).thenReturn(1);
        stubDetail(stored);

        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            service.submit(5L);
            org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()
                    .forEach(s -> s.afterCompletion(
                            org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK));
            verify(privateStorage).delete("contract/202611/new.pdf");
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("F5：事务提交后保留新生成的 PDF 文件；复用既有文件不注册清理")
    void commitKeepsGeneratedPdf() {
        // 场景一：新生成 + 提交 → 不删除
        Contract stored = contract(5L, "DRAFT", null);
        when(contractMapper.selectById(5L)).thenReturn(stored);
        when(contractMapper.countOccupyingByHouse(7L, 5L)).thenReturn(0L);
        when(pdfGenerator.generate(any(), anyString(), anyString())).thenReturn("%PDF".getBytes());
        when(privateStorage.storePdf(any())).thenReturn("contract/202611/new.pdf");
        when(contractMapper.updateStatusIfMatch(eq(5L), eq("DRAFT"), eq("PENDING_SIGN"), any())).thenReturn(1);
        stubDetail(stored);

        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            service.submit(5L);
            org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()
                    .forEach(s -> s.afterCompletion(
                            org.springframework.transaction.support.TransactionSynchronization.STATUS_COMMITTED));
            verify(privateStorage, org.mockito.Mockito.never()).delete(anyString());
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }

        // 场景二：复用既有 PDF（无新生成）→ 回滚也不删除旧文件
        Contract withPdf = contract(6L, "DRAFT", "contract/old.pdf");
        when(contractMapper.selectById(6L)).thenReturn(withPdf);
        when(privateStorage.exists("contract/old.pdf")).thenReturn(true);
        when(contractMapper.countOccupyingByHouse(7L, 6L)).thenReturn(0L);
        when(contractMapper.updateStatusIfMatch(eq(6L), eq("DRAFT"), eq("PENDING_SIGN"), any())).thenReturn(1);
        stubDetail(withPdf);

        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            service.submit(6L);
            org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()
                    .forEach(s -> s.afterCompletion(
                            org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK));
            verify(privateStorage, org.mockito.Mockito.never()).delete("contract/old.pdf");
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("客户确认：非本人合同返回 404")
    void confirmOthersReturnsNotFound() {
        Contract stored = contract(5L, "PENDING_SIGN", "contract/a.pdf");
        stored.setCustomerId(99L);
        when(contractMapper.selectById(5L)).thenReturn(stored);
        when(currentUser.requireUserId()).thenReturn(2L);

        assertThatThrownBy(() -> service.confirm(5L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("客户确认：生效后房源置 CONTRACTED 并自动完成 SIGN 节点")
    void confirmActivatesAndContractsHouse() {
        Contract stored = contract(5L, "PENDING_SIGN", "contract/a.pdf");
        when(contractMapper.selectById(5L)).thenReturn(stored);
        when(currentUser.requireUserId()).thenReturn(2L);
        when(privateStorage.exists("contract/a.pdf")).thenReturn(true);
        when(houseLifecycle.lock(7L)).thenReturn(house("RESERVED"));
        when(contractMapper.countOccupyingByHouse(7L, 5L)).thenReturn(0L);
        when(contractMapper.activate(eq(5L), any(), eq("contract/a.pdf"))).thenReturn(1);
        stubDetail(stored);

        service.confirm(5L);

        verify(houseLifecycle).markContracted(7L);
        verify(transactionService).markSigned(5L);
    }

    @Test
    @DisplayName("客户确认：并发下条件更新失败返回 409，不联动房源")
    void confirmConflict() {
        Contract stored = contract(5L, "PENDING_SIGN", "contract/a.pdf");
        when(contractMapper.selectById(5L)).thenReturn(stored);
        when(currentUser.requireUserId()).thenReturn(2L);
        when(privateStorage.exists("contract/a.pdf")).thenReturn(true);
        when(houseLifecycle.lock(7L)).thenReturn(house("RESERVED"));
        when(contractMapper.countOccupyingByHouse(7L, 5L)).thenReturn(0L);
        when(contractMapper.activate(eq(5L), any(), eq("contract/a.pdf"))).thenReturn(0);

        assertThatThrownBy(() -> service.confirm(5L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.CONFLICT);
        verify(houseLifecycle, never()).markContracted(anyLong());
    }

    @Test
    @DisplayName("终止生效合同：释放房源并取消未完成节点")
    void terminateActiveReleasesHouse() {
        Contract stored = contract(5L, "ACTIVE", "contract/a.pdf");
        when(contractMapper.selectById(5L)).thenReturn(stored);
        when(contractMapper.updateStatusIfMatch(eq(5L), eq("ACTIVE"), eq("TERMINATED"), any())).thenReturn(1);
        when(contractMapper.countOccupyingByHouse(7L, 5L)).thenReturn(0L);
        stubDetail(stored);

        service.terminate(5L, new ContractTerminateRequest("租客工作调动"));

        verify(transactionService).cancelPending(eq(5L), eq("租客工作调动"));
        verify(houseLifecycle).releaseContract(7L);
    }

    @Test
    @DisplayName("终止草稿：变为 CANCELLED，不释放房源")
    void terminateDraftCancels() {
        Contract stored = contract(5L, "DRAFT", null);
        when(contractMapper.selectById(5L)).thenReturn(stored);
        when(contractMapper.updateStatusIfMatch(eq(5L), eq("DRAFT"), eq("CANCELLED"), any())).thenReturn(1);
        stubDetail(stored);

        service.terminate(5L, new ContractTerminateRequest("信息有误"));

        verify(houseLifecycle, never()).releaseContract(anyLong());
    }

    @Test
    @DisplayName("PDF 未生成时下载返回 404")
    void downloadWithoutPdfReturns404() {
        Contract stored = contract(5L, "DRAFT", null);
        when(contractMapper.selectById(5L)).thenReturn(stored);
        when(currentUser.isAdmin()).thenReturn(false);
        when(currentUser.requireUserId()).thenReturn(2L);

        assertThatThrownBy(() -> service.downloadPdf(5L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("编辑草稿：version 冲突返回 409")
    void updateVersionConflict() {
        Contract stored = contract(5L, "DRAFT", null);
        when(contractMapper.selectById(5L)).thenReturn(stored);
        when(contractMapper.updateById(any(Contract.class))).thenReturn(0);

        ContractUpdateRequest request = new ContractUpdateRequest("新标题", null, null, null, null, null, 3);

        assertThatThrownBy(() -> service.update(5L, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    @DisplayName("列表：CUSTOMER 强制本人 customerId，ADMIN 不强制")
    void listForcesCustomerForNonAdmin() {
        when(currentUser.isAdmin()).thenReturn(false);
        when(currentUser.requireUserId()).thenReturn(2L);
        when(contractMapper.countByQuery(any(), eq(2L))).thenReturn(0L);

        service.list(new com.example.realestate.modules.contract.model.query.ContractQuery());

        verify(contractMapper).countByQuery(any(), eq(2L));
        verify(contractMapper, never()).countByQuery(any(), isNull());
    }
}
