package com.example.realestate.modules.repair.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.realestate.common.api.PageResponse;
import com.example.realestate.common.enums.ContractStatus;
import com.example.realestate.common.enums.ErrorCode;
import com.example.realestate.common.enums.RepairCategory;
import com.example.realestate.common.enums.RepairStatus;
import com.example.realestate.common.enums.RepairUrgency;
import com.example.realestate.common.exception.BusinessException;
import com.example.realestate.common.util.PhoneMasker;
import com.example.realestate.modules.contract.mapper.ContractMapper;
import com.example.realestate.modules.contract.model.entity.Contract;
import com.example.realestate.modules.file.service.PrivateStorageService;
import com.example.realestate.modules.house.mapper.HouseMapper;
import com.example.realestate.modules.house.model.entity.House;
import com.example.realestate.modules.repair.mapper.RepairMediaMapper;
import com.example.realestate.modules.repair.mapper.RepairStatusLogMapper;
import com.example.realestate.modules.repair.mapper.RepairTicketMapper;
import com.example.realestate.modules.repair.model.dto.RepairCreateRequest;
import com.example.realestate.modules.repair.model.entity.RepairMedia;
import com.example.realestate.modules.repair.model.entity.RepairStatusLog;
import com.example.realestate.modules.repair.model.entity.RepairTicket;
import com.example.realestate.modules.repair.model.query.RepairQuery;
import com.example.realestate.modules.repair.model.vo.RepairDetailRow;
import com.example.realestate.modules.repair.model.vo.RepairDetailVO;
import com.example.realestate.modules.repair.model.vo.RepairListItemVO;
import com.example.realestate.modules.repair.model.vo.RepairListRow;
import com.example.realestate.modules.repair.model.vo.RepairMediaVO;
import com.example.realestate.modules.repair.model.vo.RepairStatusLogVO;
import com.example.realestate.modules.repair.service.RepairService;
import com.example.realestate.security.CurrentUserProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 报修实现：
 *  - 列表数据权限（本人/ADMIN）与分页下推 SQL；
 *  - 状态迁移先读当前状态校验状态机，再用条件 UPDATE 防并发重复受理/关闭；
 *  - 图片落私有存储，只保存相对 key，接口按鉴权流式返回；
 *  - 电话对非 ADMIN 脱敏，日志不输出描述与图片内容。
 */
@Service
public class RepairServiceImpl implements RepairService {

    private static final Logger log = LoggerFactory.getLogger(RepairServiceImpl.class);
    private static final int MAX_MEDIA_PER_TICKET = 6;

    private final RepairTicketMapper ticketMapper;
    private final RepairMediaMapper mediaMapper;
    private final RepairStatusLogMapper statusLogMapper;
    private final HouseMapper houseMapper;
    private final ContractMapper contractMapper;
    private final PrivateStorageService privateStorage;
    private final CurrentUserProvider currentUser;

    public RepairServiceImpl(RepairTicketMapper ticketMapper,
                             RepairMediaMapper mediaMapper,
                             RepairStatusLogMapper statusLogMapper,
                             HouseMapper houseMapper,
                             ContractMapper contractMapper,
                             PrivateStorageService privateStorage,
                             CurrentUserProvider currentUser) {
        this.ticketMapper = ticketMapper;
        this.mediaMapper = mediaMapper;
        this.statusLogMapper = statusLogMapper;
        this.houseMapper = houseMapper;
        this.contractMapper = contractMapper;
        this.privateStorage = privateStorage;
        this.currentUser = currentUser;
    }

    @Override
    public PageResponse<RepairListItemVO> list(RepairQuery query) {
        RepairQuery safe = query == null ? new RepairQuery() : query;
        Long forcedCustomerId = currentUser.isAdmin() ? null : currentUser.requireUserId();
        safe.setStatus(normalizeStatus(safe.getStatus()));
        safe.setCategory(normalizeCategory(safe.getCategory()));
        if (safe.getKeyword() != null) {
            String keyword = safe.getKeyword().trim();
            if (keyword.length() > 64) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "搜索关键字过长");
            }
            safe.setKeyword(keyword.isEmpty() ? null : keyword);
        }
        long total = ticketMapper.countByQuery(safe, forcedCustomerId);
        long size = safe.normalizedSize();
        if (total == 0) {
            return PageResponse.of(List.of(), 0, safe.normalizedPage(), size);
        }
        boolean admin = currentUser.isAdmin();
        List<RepairListItemVO> items = ticketMapper
                .selectPageList(safe, forcedCustomerId, safe.offset(), size)
                .stream()
                .map(row -> fillListLabels(row, admin))
                .toList();
        return PageResponse.of(items, total, safe.normalizedPage(), size);
    }

    @Override
    public RepairDetailVO detail(Long id) {
        RepairDetailRow row = requireVisibleRow(id);
        return buildDetail(row);
    }

    @Override
    @Transactional
    public RepairDetailVO create(RepairCreateRequest request) {
        Long customerId = currentUser.requireUserId();
        Long houseId = resolveHouseId(request, customerId);
        House house = houseMapper.selectById(houseId);
        if (house == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "房源不存在");
        }
        Instant now = Instant.now();
        if (request.preferredVisitTime() != null) {
            if (request.preferredVisitTime().isBefore(now.minus(5, ChronoUnit.MINUTES))) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "期望上门时间不能早于当前时间");
            }
            if (request.preferredVisitTime().isAfter(now.plus(366, ChronoUnit.DAYS))) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "期望上门时间不能超过一年");
            }
        }

        RepairTicket entity = new RepairTicket();
        entity.setHouseId(houseId);
        entity.setContractId(request.contractId());
        entity.setCustomerId(customerId);
        entity.setCategory(request.category().name());
        entity.setUrgency(request.urgency().name());
        entity.setTitle(request.title().trim());
        entity.setDescription(request.description().trim());
        entity.setContactPhone(request.contactPhone().trim());
        entity.setPreferredVisitTime(request.preferredVisitTime());
        entity.setStatus(RepairStatus.SUBMITTED.name());
        entity.setVersion(0);
        entity.setDeleted(0);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        insertWithRetry(entity);
        writeStatusLog(entity.getId(), null, RepairStatus.SUBMITTED.name(), "客户提交报修");
        log.info("报修创建 id={} no={} houseId={} customerId={} category={} urgency={}",
                entity.getId(), entity.getTicketNo(), houseId, customerId,
                entity.getCategory(), entity.getUrgency());
        return buildDetail(requireDetailRow(entity.getId()));
    }

    @Override
    @Transactional
    public RepairDetailVO accept(Long id, String reason) {
        return transition(id, RepairStatus.ACCEPTED, defaultReason(reason, "管理员受理"));
    }

    @Override
    @Transactional
    public RepairDetailVO start(Long id, String reason) {
        return transition(id, RepairStatus.PROCESSING, defaultReason(reason, "开始处理"));
    }

    @Override
    @Transactional
    public RepairDetailVO resolve(Long id, String reason) {
        return transition(id, RepairStatus.RESOLVED, defaultReason(reason, "处理完成"));
    }

    @Override
    @Transactional
    public RepairDetailVO close(Long id, String reason) {
        return transition(id, RepairStatus.CLOSED, defaultReason(reason, "确认关闭"));
    }

    @Override
    @Transactional
    public RepairDetailVO reopen(Long id, String reason) {
        return transition(id, RepairStatus.REOPENED, defaultReason(reason, "重新打开"));
    }

    @Override
    @Transactional
    public RepairDetailVO cancel(Long id, String reason) {
        return transition(id, RepairStatus.CANCELLED, defaultReason(reason, "撤销报修"));
    }

    @Override
    @Transactional
    public RepairMediaVO addMedia(Long id, MultipartFile file) {
        RepairDetailRow row = requireVisibleRow(id);
        RepairStatus status = RepairStatus.of(row.getStatus());
        if (status == RepairStatus.CLOSED || status == RepairStatus.CANCELLED) {
            throw new BusinessException(ErrorCode.CONFLICT, "工单已结束，不能继续上传图片");
        }
        long count = mediaMapper.selectCount(Wrappers.<RepairMedia>lambdaQuery()
                .eq(RepairMedia::getTicketId, id));
        if (count >= MAX_MEDIA_PER_TICKET) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "单个工单最多上传 " + MAX_MEDIA_PER_TICKET + " 张图片");
        }
        String key = privateStorage.storeRepairImage(file);
        RepairMedia media = new RepairMedia();
        media.setTicketId(id);
        media.setUrl(key);
        media.setMediaType("IMAGE");
        media.setSort((int) count);
        media.setCreatedAt(Instant.now());
        try {
            mediaMapper.insert(media);
        } catch (RuntimeException e) {
            // 数据库写入失败时补偿删除已落盘文件，避免孤儿对象
            privateStorage.delete(key);
            throw e;
        }
        log.info("报修图片上传 ticketId={} mediaId={} count={}", id, media.getId(), count + 1);
        return toMediaVO(id, media);
    }

    @Override
    public RepairMediaContent downloadMedia(Long ticketId, Long mediaId) {
        requireVisibleRow(ticketId);
        RepairMedia media = requireMedia(ticketId, mediaId);
        String key = media.getUrl();
        byte[] content = privateStorage.read(key, "报修图片不存在");
        String extension = key.contains(".") ? key.substring(key.lastIndexOf('.') + 1) : "jpg";
        return new RepairMediaContent(content, privateStorage.contentType(key),
                "repair-" + mediaId + "." + extension);
    }

    @Override
    @Transactional
    public void deleteMedia(Long ticketId, Long mediaId) {
        RepairDetailRow row = requireVisibleRow(ticketId);
        RepairStatus status = RepairStatus.of(row.getStatus());
        if (status == RepairStatus.CLOSED || status == RepairStatus.CANCELLED) {
            throw new BusinessException(ErrorCode.CONFLICT, "工单已结束，不能删除图片");
        }
        RepairMedia media = requireMedia(ticketId, mediaId);
        mediaMapper.deleteById(media.getId());
        privateStorage.delete(media.getUrl());
        log.info("报修图片删除 ticketId={} mediaId={} operator={}", ticketId, mediaId,
                currentUser.currentUserIdOrNull());
    }

    // ---------- 内部方法 ----------

    private RepairDetailVO transition(Long id, RepairStatus target, String reason) {
        RepairDetailRow row = requireVisibleRow(id);
        RepairStatus current = RepairStatus.of(row.getStatus());
        if (current == null || !current.canTransitionTo(target)) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "报修当前状态 " + row.getStatus() + " 不允许该操作");
        }
        int rows = ticketMapper.updateStatusIfMatch(id, current.name(), target.name(), Instant.now());
        if (rows == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "工单状态已变化，请刷新后重试");
        }
        writeStatusLog(id, current.name(), target.name(), reason);
        log.info("报修状态迁移 id={} {} -> {} operator={}", id, current, target,
                currentUser.currentUserIdOrNull());
        return buildDetail(requireDetailRow(id));
    }

    private Long resolveHouseId(RepairCreateRequest request, Long customerId) {
        if (request.contractId() != null) {
            Contract contract = contractMapper.selectById(request.contractId());
            if (contract == null || !customerId.equals(contract.getCustomerId())) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "合同不存在");
            }
            if (ContractStatus.of(contract.getStatus()) != ContractStatus.ACTIVE) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "仅生效中的合同可关联报修");
            }
            if (request.houseId() != null && !request.houseId().equals(contract.getHouseId())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "合同与房源不匹配");
            }
            return contract.getHouseId();
        }
        if (request.houseId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择房源或关联合同");
        }
        return request.houseId();
    }

    private RepairDetailRow requireDetailRow(Long id) {
        RepairDetailRow row = id == null ? null : ticketMapper.selectDetailById(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "报修工单不存在");
        }
        return row;
    }

    /** CUSTOMER 访问他人报修返回 404，不泄露存在性 */
    private RepairDetailRow requireVisibleRow(Long id) {
        RepairDetailRow row = requireDetailRow(id);
        if (currentUser.isAdmin()) {
            return row;
        }
        Long userId = currentUser.requireUserId();
        if (!userId.equals(row.getCustomerId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "报修工单不存在");
        }
        return row;
    }

    private RepairMedia requireMedia(Long ticketId, Long mediaId) {
        RepairMedia media = mediaId == null ? null : mediaMapper.selectById(mediaId);
        if (media == null || !ticketId.equals(media.getTicketId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "报修图片不存在");
        }
        return media;
    }

    private RepairDetailVO buildDetail(RepairDetailRow row) {
        boolean admin = currentUser.isAdmin();
        List<RepairMediaVO> media = mediaMapper.selectList(Wrappers.<RepairMedia>lambdaQuery()
                        .eq(RepairMedia::getTicketId, row.getId())
                        .orderByAsc(RepairMedia::getSort)
                        .orderByAsc(RepairMedia::getId))
                .stream()
                .map(m -> toMediaVO(row.getId(), m))
                .toList();
        List<RepairStatusLogVO> logs = statusLogMapper.selectList(
                        Wrappers.<RepairStatusLog>lambdaQuery()
                                .eq(RepairStatusLog::getTicketId, row.getId())
                                .orderByAsc(RepairStatusLog::getCreatedAt)
                                .orderByAsc(RepairStatusLog::getId))
                .stream()
                .map(this::toLogVO)
                .toList();
        RepairStatus status = RepairStatus.of(row.getStatus());
        RepairCategory category = RepairCategory.of(row.getCategory());
        RepairUrgency urgency = RepairUrgency.of(row.getUrgency());
        return new RepairDetailVO(
                row.getId(), row.getTicketNo(), row.getHouseId(), row.getHouseTitle(), row.getHouseAddress(),
                row.getContractId(), row.getContractNo(), row.getCustomerId(), row.getCustomerName(),
                row.getCategory(), category == null ? row.getCategory() : category.label(),
                row.getUrgency(), urgency == null ? row.getUrgency() : urgency.label(),
                row.getTitle(), row.getDescription(),
                row.getStatus(), status == null ? row.getStatus() : status.label(),
                admin ? row.getContactPhone() : PhoneMasker.mask(row.getContactPhone()),
                row.getPreferredVisitTime(), row.getVersion(), row.getCreatedAt(), row.getUpdatedAt(),
                media, logs);
    }

    private RepairListItemVO fillListLabels(RepairListRow row, boolean admin) {
        RepairStatus status = RepairStatus.of(row.getStatus());
        RepairCategory category = RepairCategory.of(row.getCategory());
        RepairUrgency urgency = RepairUrgency.of(row.getUrgency());
        return new RepairListItemVO(
                row.getId(), row.getTicketNo(), row.getHouseId(), row.getHouseTitle(),
                row.getContractId(), row.getContractNo(), row.getCustomerId(), row.getCustomerName(),
                row.getCategory(), category == null ? row.getCategory() : category.label(),
                row.getUrgency(), urgency == null ? row.getUrgency() : urgency.label(),
                row.getTitle(), row.getStatus(), status == null ? row.getStatus() : status.label(),
                admin ? row.getContactPhone() : PhoneMasker.mask(row.getContactPhone()),
                row.getPreferredVisitTime(), row.getCreatedAt(), row.getUpdatedAt());
    }

    private RepairMediaVO toMediaVO(Long ticketId, RepairMedia media) {
        return new RepairMediaVO(media.getId(),
                "/repairs/" + ticketId + "/media/" + media.getId(),
                media.getMediaType(), media.getSort(), media.getCreatedAt());
    }

    private RepairStatusLogVO toLogVO(RepairStatusLog log) {
        RepairStatus from = RepairStatus.of(log.getFromStatus());
        RepairStatus to = RepairStatus.of(log.getToStatus());
        return new RepairStatusLogVO(log.getId(), log.getFromStatus(),
                from == null ? null : from.label(), log.getToStatus(),
                to == null ? log.getToStatus() : to.label(),
                log.getOperatorId(), log.getReason(), log.getCreatedAt());
    }

    private void writeStatusLog(Long ticketId, String from, String to, String reason) {
        RepairStatusLog statusLog = new RepairStatusLog();
        statusLog.setTicketId(ticketId);
        statusLog.setFromStatus(from);
        statusLog.setToStatus(to);
        statusLog.setOperatorId(currentUser.currentUserIdOrNull());
        statusLog.setReason(reason);
        statusLog.setCreatedAt(Instant.now());
        statusLogMapper.insert(statusLog);
    }

    /** 单号冲突自动重试；最多 3 次 */
    private void insertWithRetry(RepairTicket entity) {
        for (int attempt = 0; attempt < 3; attempt++) {
            entity.setTicketNo(nextTicketNo());
            try {
                ticketMapper.insert(entity);
                return;
            } catch (DuplicateKeyException e) {
                log.warn("报修单号冲突，重试 attempt={}", attempt + 1);
            }
        }
        throw new BusinessException(ErrorCode.UNEXPECTED, "报修单号生成冲突，请重试");
    }

    private String nextTicketNo() {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        return "BX" + date + "-" + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        RepairStatus parsed = RepairStatus.of(status);
        if (parsed == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "报修状态不合法");
        }
        return parsed.name();
    }

    private String normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            return null;
        }
        RepairCategory parsed = RepairCategory.of(category);
        if (parsed == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "报修分类不合法");
        }
        return parsed.name();
    }

    private String defaultReason(String reason, String fallback) {
        if (reason == null || reason.isBlank()) {
            return fallback;
        }
        String trimmed = reason.trim();
        return trimmed.length() > 500 ? trimmed.substring(0, 500) : trimmed;
    }
}
