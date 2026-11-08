package com.example.realestate.modules.appointment.service.impl;

import com.example.realestate.common.api.PageResponse;
import com.example.realestate.common.enums.AppointmentStatus;
import com.example.realestate.common.enums.ErrorCode;
import com.example.realestate.common.enums.HouseStatus;
import com.example.realestate.common.exception.BusinessException;
import com.example.realestate.modules.appointment.convert.AppointmentConvert;
import com.example.realestate.modules.appointment.mapper.ViewingAppointmentMapper;
import com.example.realestate.modules.appointment.model.dto.AppointmentCreateRequest;
import com.example.realestate.modules.appointment.model.entity.ViewingAppointment;
import com.example.realestate.modules.appointment.model.query.AppointmentQuery;
import com.example.realestate.modules.appointment.model.vo.AppointmentVO;
import com.example.realestate.modules.appointment.service.AppointmentService;
import com.example.realestate.modules.house.mapper.HouseMapper;
import com.example.realestate.modules.house.model.entity.House;
import com.example.realestate.modules.house.service.HouseLifecycleService;
import com.example.realestate.security.CurrentUserProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 预约带看实现：
 *  - 列表数据权限与条件全部下推 SQL（forcedCustomerId），不在内存过滤；
 *  - 状态迁移使用「条件 UPDATE ... WHERE status = expected」，并发下只有一次成功，其余 409；
 *  - 确认预约在同一事务内把房源 PUBLISHED → RESERVED，取消/完成/爽约时按剩余预约释放。
 */
@Service
public class AppointmentServiceImpl implements AppointmentService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentServiceImpl.class);

    private static final ZoneId CN_ZONE = ZoneId.of("Asia/Shanghai");
    private static final long MAX_ADVANCE_DAYS = 180L;

    private final ViewingAppointmentMapper appointmentMapper;
    private final HouseMapper houseMapper;
    private final HouseLifecycleService houseLifecycle;
    private final AppointmentConvert appointmentConvert;
    private final CurrentUserProvider currentUser;

    public AppointmentServiceImpl(ViewingAppointmentMapper appointmentMapper,
                                  HouseMapper houseMapper,
                                  HouseLifecycleService houseLifecycle,
                                  AppointmentConvert appointmentConvert,
                                  CurrentUserProvider currentUser) {
        this.appointmentMapper = appointmentMapper;
        this.houseMapper = houseMapper;
        this.houseLifecycle = houseLifecycle;
        this.appointmentConvert = appointmentConvert;
        this.currentUser = currentUser;
    }

    @Override
    public PageResponse<AppointmentVO> list(AppointmentQuery query) {
        AppointmentQuery safe = query == null ? new AppointmentQuery() : query;
        boolean admin = currentUser.isAdmin();
        Long forcedCustomerId = admin ? null : currentUser.requireUserId();
        if (!admin) {
            // 忽略客户端传入的 customerId，数据权限不接受外部指定
            safe.setCustomerId(null);
        }
        safe.setStatus(normalizeStatus(safe.getStatus()));
        if (safe.getFromTime() != null && safe.getToTime() != null
                && safe.getFromTime().isAfter(safe.getToTime())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "时间区间不合法");
        }

        long total = appointmentMapper.countByQuery(safe, forcedCustomerId);
        long size = safe.normalizedSize();
        if (total == 0) {
            return PageResponse.of(List.of(), 0, safe.normalizedPage(), size);
        }
        List<AppointmentVO> items = appointmentMapper
                .selectPageList(safe, forcedCustomerId, safe.offset(), size)
                .stream()
                .map(vo -> appointmentConvert.forViewer(vo, admin))
                .toList();
        return PageResponse.of(items, total, safe.normalizedPage(), size);
    }

    @Override
    public AppointmentVO detail(Long id) {
        AppointmentVO vo = id == null ? null : appointmentMapper.selectVoById(id);
        if (vo == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "预约不存在");
        }
        requireVisible(vo);
        return appointmentConvert.forViewer(vo, currentUser.isAdmin());
    }

    @Override
    @Transactional
    public AppointmentVO create(AppointmentCreateRequest request) {
        Long customerId = currentUser.requireUserId();
        House house = houseMapper.selectById(request.houseId());
        // 非 PUBLISHED 房源不可预约；对客户统一 404，避免泄露未上架房源
        if (house == null || !HouseStatus.PUBLISHED.name().equals(house.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "房源不存在或暂不可预约");
        }
        Instant preferredTime = parsePreferredTime(request.preferredTime());
        Instant now = Instant.now();
        if (preferredTime.isBefore(now)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "看房时间不能早于当前时间");
        }
        if (preferredTime.isAfter(now.plus(MAX_ADVANCE_DAYS, ChronoUnit.DAYS))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅支持预约 " + MAX_ADVANCE_DAYS + " 天内的看房时间");
        }
        long duplicated = appointmentMapper.selectCount(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<ViewingAppointment>lambdaQuery()
                        .eq(ViewingAppointment::getHouseId, request.houseId())
                        .eq(ViewingAppointment::getCustomerId, customerId)
                        .in(ViewingAppointment::getStatus,
                                AppointmentStatus.PENDING.name(), AppointmentStatus.CONFIRMED.name()));
        if (duplicated > 0) {
            throw new BusinessException(ErrorCode.DUPLICATE, "该房源已有待处理或已确认的预约");
        }

        ViewingAppointment entity = new ViewingAppointment();
        entity.setHouseId(request.houseId());
        entity.setCustomerId(customerId);
        entity.setPreferredTime(preferredTime);
        entity.setContactPhone(request.contactPhone().trim());
        entity.setRemark(trimToNull(request.remark()));
        entity.setStatus(AppointmentStatus.PENDING.name());
        entity.setVersion(0);
        entity.setDeleted(0);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        appointmentMapper.insert(entity);
        log.info("预约创建 id={} houseId={} preferredTime={}", entity.getId(), entity.getHouseId(), preferredTime);
        return appointmentConvert.forViewer(appointmentMapper.selectVoById(entity.getId()), false);
    }

    @Override
    @Transactional
    public AppointmentVO confirm(Long id) {
        ViewingAppointment entity = requireEntity(id);
        requireCanTransition(entity, AppointmentStatus.CONFIRMED);
        transition(entity, AppointmentStatus.CONFIRMED);
        // 同一事务：占房失败则整体回滚，预约保持待确认
        houseLifecycle.reserve(entity.getHouseId());
        log.info("预约确认 id={} houseId={} operator={}", id, entity.getHouseId(), currentUser.currentUserIdOrNull());
        return detail(id);
    }

    @Override
    @Transactional
    public AppointmentVO cancel(Long id) {
        ViewingAppointment entity = requireEntity(id);
        requireAdminOrOwner(entity);
        requireCanTransition(entity, AppointmentStatus.CANCELLED);
        AppointmentStatus current = AppointmentStatus.of(entity.getStatus());
        transition(entity, AppointmentStatus.CANCELLED);
        if (current == AppointmentStatus.CONFIRMED) {
            releaseIfNoConfirmed(id, entity.getHouseId());
        }
        return detail(id);
    }

    @Override
    @Transactional
    public AppointmentVO complete(Long id) {
        ViewingAppointment entity = requireEntity(id);
        requireCanTransition(entity, AppointmentStatus.COMPLETED);
        transition(entity, AppointmentStatus.COMPLETED);
        releaseIfNoConfirmed(id, entity.getHouseId());
        return detail(id);
    }

    @Override
    @Transactional
    public AppointmentVO noShow(Long id) {
        ViewingAppointment entity = requireEntity(id);
        requireCanTransition(entity, AppointmentStatus.NO_SHOW);
        transition(entity, AppointmentStatus.NO_SHOW);
        releaseIfNoConfirmed(id, entity.getHouseId());
        return detail(id);
    }

    // ---------- 内部方法 ----------

    private ViewingAppointment requireEntity(Long id) {
        ViewingAppointment entity = id == null ? null : appointmentMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "预约不存在");
        }
        return entity;
    }

    /** CUSTOMER 访问他人预约返回 404，减少存在性泄露 */
    private void requireVisible(AppointmentVO vo) {
        if (currentUser.isAdmin()) {
            return;
        }
        Long userId = currentUser.requireUserId();
        if (!userId.equals(vo.getCustomerId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "预约不存在");
        }
    }

    private void requireAdminOrOwner(ViewingAppointment entity) {
        if (currentUser.isAdmin()) {
            return;
        }
        Long userId = currentUser.requireUserId();
        if (!userId.equals(entity.getCustomerId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "预约不存在");
        }
    }

    private void requireCanTransition(ViewingAppointment entity, AppointmentStatus target) {
        AppointmentStatus current = AppointmentStatus.of(entity.getStatus());
        if (current == null || !current.canTransitionTo(target)) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "预约当前状态 " + entity.getStatus() + " 不允许变更为 " + target.name());
        }
    }

    /** 条件更新：并发重复操作只有一个成功 */
    private void transition(ViewingAppointment entity, AppointmentStatus target) {
        int rows = appointmentMapper.updateStatusIfMatch(entity.getId(), entity.getStatus(), target.name(), Instant.now());
        if (rows == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "预约状态已变化，请刷新后重试");
        }
    }

    private void releaseIfNoConfirmed(Long appointmentId, Long houseId) {
        long remaining = appointmentMapper.countConfirmedByHouse(houseId, appointmentId);
        if (remaining == 0) {
            houseLifecycle.releaseReservation(houseId);
        }
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        AppointmentStatus parsed = AppointmentStatus.of(status);
        if (parsed == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "预约状态不合法");
        }
        return parsed.name();
    }

    /** 兼容三种入参：带 Z、带偏移、无时区本地时间（按 Asia/Shanghai） */
    private Instant parsePreferredTime(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "看房时间不能为空");
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return java.time.OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return LocalDateTime.parse(value).atZone(CN_ZONE).toInstant();
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "看房时间格式不正确");
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
