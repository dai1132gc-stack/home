package com.example.realestate.modules.appointment.service.impl;

import com.example.realestate.common.api.PageResponse;
import com.example.realestate.common.enums.AppointmentStatus;
import com.example.realestate.common.enums.ErrorCode;
import com.example.realestate.common.exception.BusinessException;
import com.example.realestate.modules.appointment.convert.AppointmentConvert;
import com.example.realestate.modules.appointment.mapper.ViewingAppointmentMapper;
import com.example.realestate.modules.appointment.model.dto.AppointmentCreateRequest;
import com.example.realestate.modules.appointment.model.entity.ViewingAppointment;
import com.example.realestate.modules.appointment.model.query.AppointmentQuery;
import com.example.realestate.modules.appointment.model.vo.AppointmentVO;
import com.example.realestate.modules.house.mapper.HouseMapper;
import com.example.realestate.modules.house.model.entity.House;
import com.example.realestate.modules.house.service.HouseLifecycleService;
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
@DisplayName("预约服务：数据权限、状态机与房源联动")
class AppointmentServiceImplTest {

    @Mock
    private ViewingAppointmentMapper appointmentMapper;
    @Mock
    private HouseMapper houseMapper;
    @Mock
    private HouseLifecycleService houseLifecycle;
    @Mock
    private CurrentUserProvider currentUser;

    private AppointmentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AppointmentServiceImpl(appointmentMapper, houseMapper, houseLifecycle,
                new AppointmentConvert(), currentUser);
    }

    private House house(String status) {
        House house = new House();
        house.setId(7L);
        house.setTitle("测试房源");
        house.setStatus(status);
        house.setVersion(0);
        return house;
    }

    private ViewingAppointment appointment(Long id, Long customerId, String status) {
        ViewingAppointment entity = new ViewingAppointment();
        entity.setId(id);
        entity.setHouseId(7L);
        entity.setCustomerId(customerId);
        entity.setStatus(status);
        entity.setContactPhone("13912345678");
        entity.setPreferredTime(Instant.parse("2026-11-10T02:30:00Z"));
        entity.setVersion(0);
        return entity;
    }

    private AppointmentVO vo(Long id, Long customerId, String status) {
        AppointmentVO vo = new AppointmentVO();
        vo.setId(id);
        vo.setHouseId(7L);
        vo.setCustomerId(customerId);
        vo.setStatus(status);
        vo.setContactPhone("13912345678");
        return vo;
    }

    @Test
    @DisplayName("CUSTOMER 列表强制 customerId=本人，忽略客户端传值")
    void customerListForcesOwnId() {
        when(currentUser.isAdmin()).thenReturn(false);
        when(currentUser.requireUserId()).thenReturn(2L);
        when(appointmentMapper.countByQuery(any(), eq(2L))).thenReturn(0L);

        AppointmentQuery query = new AppointmentQuery();
        query.setCustomerId(99L);
        PageResponse<AppointmentVO> result = service.list(query);

        assertThat(result.total()).isZero();
        assertThat(query.getCustomerId()).isNull();
        verify(appointmentMapper).countByQuery(any(), eq(2L));
    }

    @Test
    @DisplayName("ADMIN 列表不强制客户，可按状态筛选")
    void adminListDoesNotForceCustomer() {
        when(currentUser.isAdmin()).thenReturn(true);
        when(appointmentMapper.countByQuery(any(), isNull())).thenReturn(1L);
        when(appointmentMapper.selectPageList(any(), isNull(), anyLong(), anyLong()))
                .thenReturn(List.of(vo(1L, 2L, "PENDING")));

        AppointmentQuery query = new AppointmentQuery();
        query.setStatus("pending");
        PageResponse<AppointmentVO> result = service.list(query);

        assertThat(result.items()).hasSize(1);
        assertThat(query.getStatus()).isEqualTo("PENDING");
        assertThat(result.items().get(0).getStatusLabel()).isEqualTo("待确认");
        assertThat(result.items().get(0).getContactPhone()).isEqualTo("13912345678");
    }

    @Test
    @DisplayName("客户看到的联系电话脱敏，客户信息不返回")
    void customerViewMasksPhone() {
        AppointmentVO vo = vo(1L, 2L, "CONFIRMED");
        vo.setCustomerName("演示客户");
        when(currentUser.isAdmin()).thenReturn(false);
        when(currentUser.requireUserId()).thenReturn(2L);
        when(appointmentMapper.selectVoById(1L)).thenReturn(vo);

        AppointmentVO detail = service.detail(1L);

        assertThat(detail.getContactPhone()).isEqualTo("139******78");
        assertThat(detail.getCustomerName()).isNull();
    }

    @Test
    @DisplayName("客户访问他人预约返回 404")
    void customerCannotReadOthers() {
        when(currentUser.isAdmin()).thenReturn(false);
        when(currentUser.requireUserId()).thenReturn(2L);
        when(appointmentMapper.selectVoById(1L)).thenReturn(vo(1L, 99L, "PENDING"));

        assertThatThrownBy(() -> service.detail(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("预约非 PUBLISHED 房源返回 404")
    void createRequiresPublishedHouse() {
        when(currentUser.requireUserId()).thenReturn(2L);
        when(houseMapper.selectById(7L)).thenReturn(house("OFFLINE"));

        AppointmentCreateRequest request = new AppointmentCreateRequest(7L,
                "2026-11-10T10:30", "13912345678", null);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
        verify(appointmentMapper, never()).insert(any(com.example.realestate.modules.appointment.model.entity.ViewingAppointment.class));
    }

    @Test
    @DisplayName("重复预约同一房源返回 409")
    void createRejectsDuplicate() {
        when(currentUser.requireUserId()).thenReturn(2L);
        when(houseMapper.selectById(7L)).thenReturn(house("PUBLISHED"));
        when(appointmentMapper.selectCount(any())).thenReturn(1L);

        AppointmentCreateRequest request = new AppointmentCreateRequest(7L,
                "2026-11-10T10:30", "13912345678", null);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.DUPLICATE);
    }

    @Test
    @DisplayName("提交预约：本地时间按 Asia/Shanghai 解析，状态固定 PENDING")
    void createPersistsPending() {
        when(currentUser.requireUserId()).thenReturn(2L);
        when(houseMapper.selectById(7L)).thenReturn(house("PUBLISHED"));
        when(appointmentMapper.selectCount(any())).thenReturn(0L);
        when(appointmentMapper.insert(any(ViewingAppointment.class))).thenAnswer(invocation -> {
            ViewingAppointment entity = invocation.getArgument(0);
            entity.setId(11L);
            return 1;
        });
        when(appointmentMapper.selectVoById(11L)).thenReturn(vo(11L, 2L, "PENDING"));

        AppointmentCreateRequest request = new AppointmentCreateRequest(7L,
                "2026-11-10T10:30", "13912345678", " 希望看采光 ");

        AppointmentVO created = service.create(request);

        assertThat(created.getId()).isEqualTo(11L);
        ArgumentCaptor<ViewingAppointment> captor = ArgumentCaptor.forClass(ViewingAppointment.class);
        verify(appointmentMapper).insert(captor.capture());
        ViewingAppointment saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(AppointmentStatus.PENDING.name());
        assertThat(saved.getPreferredTime()).isEqualTo(Instant.parse("2026-11-10T02:30:00Z"));
        assertThat(saved.getRemark()).isEqualTo("希望看采光");
    }

    @Test
    @DisplayName("确认预约：条件更新失败返回 409，不再占用房源")
    void confirmConflict() {
        when(appointmentMapper.selectById(1L)).thenReturn(appointment(1L, 2L, "PENDING"));
        when(appointmentMapper.updateStatusIfMatch(eq(1L), any(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.confirm(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.CONFLICT);
        verify(houseLifecycle, never()).reserve(anyLong());
    }

    @Test
    @DisplayName("确认预约：状态迁移成功且房源置 RESERVED")
    void confirmReservesHouse() {
        when(appointmentMapper.selectById(1L)).thenReturn(appointment(1L, 2L, "PENDING"));
        when(appointmentMapper.updateStatusIfMatch(eq(1L), eq("PENDING"), eq("CONFIRMED"), any()))
                .thenReturn(1);
        when(currentUser.isAdmin()).thenReturn(true);
        when(appointmentMapper.selectVoById(1L)).thenReturn(vo(1L, 2L, "CONFIRMED"));

        service.confirm(1L);

        verify(houseLifecycle).reserve(7L);
    }

    @Test
    @DisplayName("客户取消本人已确认预约：无其他确认预约时释放房源")
    void cancelConfirmedReleasesHouse() {
        when(appointmentMapper.selectById(1L)).thenReturn(appointment(1L, 2L, "CONFIRMED"));
        when(currentUser.isAdmin()).thenReturn(false);
        when(currentUser.requireUserId()).thenReturn(2L);
        when(appointmentMapper.updateStatusIfMatch(eq(1L), eq("CONFIRMED"), eq("CANCELLED"), any()))
                .thenReturn(1);
        when(appointmentMapper.countConfirmedByHouse(7L, 1L)).thenReturn(0L);
        when(appointmentMapper.selectVoById(1L)).thenReturn(vo(1L, 2L, "CANCELLED"));

        service.cancel(1L);

        verify(houseLifecycle).releaseReservation(7L);
    }

    @Test
    @DisplayName("客户取消他人预约返回 404")
    void cancelOthersReturnsNotFound() {
        when(appointmentMapper.selectById(1L)).thenReturn(appointment(1L, 99L, "PENDING"));
        when(currentUser.isAdmin()).thenReturn(false);
        when(currentUser.requireUserId()).thenReturn(2L);

        assertThatThrownBy(() -> service.cancel(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("已完成预约不能再取消")
    void completedCannotCancel() {
        when(appointmentMapper.selectById(1L)).thenReturn(appointment(1L, 2L, "COMPLETED"));
        when(currentUser.isAdmin()).thenReturn(false);
        when(currentUser.requireUserId()).thenReturn(2L);

        assertThatThrownBy(() -> service.cancel(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }
}
