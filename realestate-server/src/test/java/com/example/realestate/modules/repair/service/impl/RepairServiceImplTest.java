package com.example.realestate.modules.repair.service.impl;

import com.example.realestate.common.api.PageResponse;
import com.example.realestate.common.enums.ContractStatus;
import com.example.realestate.common.enums.ErrorCode;
import com.example.realestate.common.enums.RepairCategory;
import com.example.realestate.common.enums.RepairUrgency;
import com.example.realestate.common.exception.BusinessException;
import com.example.realestate.modules.contract.mapper.ContractMapper;
import com.example.realestate.modules.contract.model.entity.Contract;
import com.example.realestate.modules.file.service.PrivateStorageService;
import com.example.realestate.modules.house.mapper.HouseMapper;
import com.example.realestate.modules.repair.mapper.RepairMediaMapper;
import com.example.realestate.modules.repair.mapper.RepairStatusLogMapper;
import com.example.realestate.modules.repair.mapper.RepairTicketMapper;
import com.example.realestate.modules.repair.model.dto.RepairCreateRequest;
import com.example.realestate.modules.repair.model.entity.RepairStatusLog;
import com.example.realestate.modules.repair.model.query.RepairQuery;
import com.example.realestate.modules.repair.model.vo.RepairDetailRow;
import com.example.realestate.modules.repair.model.vo.RepairListItemVO;
import com.example.realestate.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("报修服务：状态机、数据权限、图片上限")
class RepairServiceImplTest {

    @Mock
    private RepairTicketMapper ticketMapper;
    @Mock
    private RepairMediaMapper mediaMapper;
    @Mock
    private RepairStatusLogMapper statusLogMapper;
    @Mock
    private HouseMapper houseMapper;
    @Mock
    private ContractMapper contractMapper;
    @Mock
    private PrivateStorageService privateStorage;
    @Mock
    private CurrentUserProvider currentUser;
    @Mock
    private MultipartFile multipartFile;

    private RepairServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RepairServiceImpl(ticketMapper, mediaMapper, statusLogMapper, houseMapper,
                contractMapper, privateStorage, currentUser);
    }

    private RepairDetailRow row(Long id, Long customerId, String status) {
        RepairDetailRow row = new RepairDetailRow();
        row.setId(id);
        row.setTicketNo("BX20261108-000001");
        row.setHouseId(7L);
        row.setHouseTitle("测试房源");
        row.setCustomerId(customerId);
        row.setCategory(RepairCategory.PLUMBING.name());
        row.setUrgency(RepairUrgency.URGENT.name());
        row.setTitle("厨房漏水");
        row.setDescription("水槽下方持续渗水，地面已积水");
        row.setStatus(status);
        row.setContactPhone("13912345678");
        row.setVersion(0);
        row.setCreatedAt(Instant.parse("2026-11-08T02:00:00Z"));
        row.setUpdatedAt(Instant.parse("2026-11-08T02:00:00Z"));
        return row;
    }

    @Test
    @DisplayName("CUSTOMER 列表强制本人 customerId 下推 SQL")
    void customerListForcesOwnId() {
        when(currentUser.isAdmin()).thenReturn(false);
        when(currentUser.requireUserId()).thenReturn(2L);
        when(ticketMapper.countByQuery(any(), eq(2L))).thenReturn(0L);

        PageResponse<RepairListItemVO> result = service.list(new RepairQuery());

        assertThat(result.total()).isZero();
        verify(ticketMapper).countByQuery(any(), eq(2L));
    }

    @Test
    @DisplayName("ADMIN 列表不强制客户，电话明文；客户视角脱敏")
    void phoneMasking() {
        when(currentUser.isAdmin()).thenReturn(true);
        when(ticketMapper.countByQuery(any(), isNull())).thenReturn(1L);
        when(ticketMapper.selectPageList(any(), isNull(), anyLong(), anyLong()))
                .thenReturn(List.of(listRow()));

        PageResponse<RepairListItemVO> admin = service.list(new RepairQuery());
        assertThat(admin.items().get(0).contactPhone()).isEqualTo("13912345678");

        when(currentUser.isAdmin()).thenReturn(false);
        when(currentUser.requireUserId()).thenReturn(2L);
        when(ticketMapper.countByQuery(any(), eq(2L))).thenReturn(1L);
        when(ticketMapper.selectPageList(any(), eq(2L), anyLong(), anyLong()))
                .thenReturn(List.of(listRow()));

        PageResponse<RepairListItemVO> customer = service.list(new RepairQuery());
        assertThat(customer.items().get(0).contactPhone()).isEqualTo("139******78");
    }

    private com.example.realestate.modules.repair.model.vo.RepairListRow listRow() {
        var row = new com.example.realestate.modules.repair.model.vo.RepairListRow();
        row.setId(1L);
        row.setTicketNo("BX20261108-000001");
        row.setHouseId(7L);
        row.setCustomerId(2L);
        row.setCategory("PLUMBING");
        row.setUrgency("URGENT");
        row.setTitle("厨房漏水");
        row.setStatus("SUBMITTED");
        row.setContactPhone("13912345678");
        row.setCreatedAt(Instant.parse("2026-11-08T02:00:00Z"));
        row.setUpdatedAt(Instant.parse("2026-11-08T02:00:00Z"));
        return row;
    }

    @Test
    @DisplayName("客户访问他人报修返回 404")
    void customerCannotReadOthers() {
        when(currentUser.isAdmin()).thenReturn(false);
        when(currentUser.requireUserId()).thenReturn(2L);
        when(ticketMapper.selectDetailById(1L)).thenReturn(row(1L, 99L, "SUBMITTED"));

        assertThatThrownBy(() -> service.detail(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("非法迁移：处理中不能再次受理（409）")
    void illegalTransition() {
        when(currentUser.isAdmin()).thenReturn(true);
        when(ticketMapper.selectDetailById(1L)).thenReturn(row(1L, 2L, "PROCESSING"));

        assertThatThrownBy(() -> service.accept(1L, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.CONFLICT);
        verify(ticketMapper, never()).updateStatusIfMatch(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("并发重复受理：条件更新 0 行返回 409")
    void concurrentAcceptConflict() {
        when(currentUser.isAdmin()).thenReturn(true);
        when(ticketMapper.selectDetailById(1L)).thenReturn(row(1L, 2L, "SUBMITTED"));
        when(ticketMapper.updateStatusIfMatch(eq(1L), eq("SUBMITTED"), eq("ACCEPTED"), any()))
                .thenReturn(0);

        assertThatThrownBy(() -> service.accept(1L, "受理"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.CONFLICT);
        verify(statusLogMapper, never()).insert(any(RepairStatusLog.class));
    }

    @Test
    @DisplayName("受理成功写一条状态日志")
    void acceptWritesLog() {
        when(currentUser.isAdmin()).thenReturn(true);
        when(currentUser.currentUserIdOrNull()).thenReturn(1L);
        when(ticketMapper.selectDetailById(1L)).thenReturn(row(1L, 2L, "SUBMITTED"));
        when(ticketMapper.updateStatusIfMatch(eq(1L), eq("SUBMITTED"), eq("ACCEPTED"), any()))
                .thenReturn(1);

        service.accept(1L, "已安排师傅");

        ArgumentCaptor<RepairStatusLog> captor = ArgumentCaptor.forClass(RepairStatusLog.class);
        verify(statusLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getFromStatus()).isEqualTo("SUBMITTED");
        assertThat(captor.getValue().getToStatus()).isEqualTo("ACCEPTED");
        assertThat(captor.getValue().getReason()).isEqualTo("已安排师傅");
        assertThat(captor.getValue().getOperatorId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("关联他人合同报修返回 404，不泄露合同存在性")
    void createWithOthersContract() {
        when(currentUser.requireUserId()).thenReturn(2L);
        Contract contract = new Contract();
        contract.setId(8L);
        contract.setCustomerId(99L);
        contract.setHouseId(7L);
        contract.setStatus(ContractStatus.ACTIVE.name());
        when(contractMapper.selectById(8L)).thenReturn(contract);

        RepairCreateRequest request = new RepairCreateRequest(null, 8L, RepairCategory.PLUMBING,
                RepairUrgency.NORMAL, "厨房漏水", "水槽下方持续渗水，地面已积水", "13912345678", null);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
        verify(ticketMapper, never()).insert(any(com.example.realestate.modules.repair.model.entity.RepairTicket.class));
    }

    @Test
    @DisplayName("图片数量上限 6 张：第 7 张拒绝且不落存储")
    void mediaLimit() {
        when(currentUser.isAdmin()).thenReturn(false);
        when(currentUser.requireUserId()).thenReturn(2L);
        when(ticketMapper.selectDetailById(1L)).thenReturn(row(1L, 2L, "PROCESSING"));
        when(mediaMapper.selectCount(any())).thenReturn(6L);

        assertThatThrownBy(() -> service.addMedia(1L, multipartFile))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
        verify(privateStorage, never()).storeRepairImage(any());
    }
}
