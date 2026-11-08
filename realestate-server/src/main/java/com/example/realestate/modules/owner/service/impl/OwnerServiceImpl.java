package com.example.realestate.modules.owner.service.impl;

import com.example.realestate.common.api.PageResponse;
import com.example.realestate.common.enums.ErrorCode;
import com.example.realestate.common.enums.OwnerType;
import com.example.realestate.common.exception.BusinessException;
import com.example.realestate.modules.owner.convert.OwnerConvert;
import com.example.realestate.modules.owner.mapper.OwnerProfileMapper;
import com.example.realestate.modules.owner.model.dto.OwnerCreateRequest;
import com.example.realestate.modules.owner.model.dto.OwnerUpdateRequest;
import com.example.realestate.modules.owner.model.entity.OwnerProfile;
import com.example.realestate.modules.owner.model.query.OwnerQuery;
import com.example.realestate.modules.owner.model.vo.OwnerVO;
import com.example.realestate.modules.owner.service.OwnerService;
import com.example.realestate.security.CurrentUserProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 客源服务：仅 ADMIN 可达（接口层 @PreAuthorize 已限权），列表条件与分页全部下推 SQL。
 */
@Service
public class OwnerServiceImpl implements OwnerService {

    private static final Logger log = LoggerFactory.getLogger(OwnerServiceImpl.class);

    private final OwnerProfileMapper ownerMapper;
    private final OwnerConvert ownerConvert;
    private final CurrentUserProvider currentUser;

    public OwnerServiceImpl(OwnerProfileMapper ownerMapper,
                            OwnerConvert ownerConvert,
                            CurrentUserProvider currentUser) {
        this.ownerMapper = ownerMapper;
        this.ownerConvert = ownerConvert;
        this.currentUser = currentUser;
    }

    @Override
    public PageResponse<OwnerVO> list(OwnerQuery query) {
        OwnerQuery safe = query == null ? new OwnerQuery() : query;
        String keyword = normalizeKeyword(safe.getKeyword());
        // 纯数字走手机号索引，其余走姓名索引：单列前缀匹配，避免 OR 导致索引失效
        boolean digits = keyword != null && keyword.chars().allMatch(Character::isDigit);
        safe.setNameKeyword(digits ? null : keyword);
        safe.setPhoneKeyword(digits ? keyword : null);
        safe.setType(normalizeType(safe.getType()));
        long offset = safe.offset();
        long size = safe.normalizedSize();

        long total = ownerMapper.countByQuery(safe);
        List<OwnerVO> items = total == 0
                ? List.of()
                : ownerMapper.selectPageList(safe, offset, size).stream()
                        .map(o -> ownerConvert.toVO(o, true))
                        .toList();
        return PageResponse.of(items, total, safe.normalizedPage(), size);
    }

    @Override
    public OwnerVO get(Long id) {
        return ownerConvert.toVO(requireEntity(id), true);
    }

    @Override
    @Transactional
    public OwnerVO create(OwnerCreateRequest request) {
        Instant now = Instant.now();
        OwnerProfile entity = new OwnerProfile();
        entity.setName(request.name().trim());
        entity.setPhone(request.phone().trim());
        entity.setType(request.type().name());
        entity.setRemark(trimToNull(request.remark()));
        entity.setCreatedBy(currentUser.currentUserIdOrNull());
        entity.setVersion(0);
        entity.setDeleted(0);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        ownerMapper.insert(entity);
        log.info("客源创建成功 id={} type={} operator={}", entity.getId(), entity.getType(),
                currentUser.currentUserIdOrNull());
        return ownerConvert.toVO(entity, true);
    }

    @Override
    @Transactional
    public OwnerVO update(Long id, OwnerUpdateRequest request) {
        OwnerProfile existing = requireEntity(id);
        OwnerProfile update = new OwnerProfile();
        update.setId(id);
        if (request.name() != null && !request.name().isBlank()) {
            update.setName(request.name().trim());
        }
        if (request.phone() != null && !request.phone().isBlank()) {
            update.setPhone(request.phone().trim());
        }
        if (request.type() != null) {
            update.setType(request.type().name());
        }
        if (request.remark() != null) {
            update.setRemark(trimToNull(request.remark()));
        }
        update.setVersion(request.version() != null ? request.version() : existing.getVersion());
        update.setUpdatedAt(Instant.now());

        int rows = ownerMapper.updateById(update);
        if (rows == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "客源信息已被修改，请刷新后重试");
        }
        return ownerConvert.toVO(ownerMapper.selectById(id), true);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        requireEntity(id);
        ownerMapper.deleteById(id);
        log.info("客源逻辑删除 id={} operator={}", id, currentUser.currentUserIdOrNull());
    }

    @Override
    public OwnerProfile requireEntity(Long id) {
        OwnerProfile entity = id == null ? null : ownerMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "房东/客源不存在");
        }
        return entity;
    }

    @Override
    public String findName(Long id) {
        if (id == null) {
            return null;
        }
        OwnerProfile entity = ownerMapper.selectById(id);
        return entity == null ? null : entity.getName();
    }

    @Override
    public OwnerProfile findEntityOrNull(Long id) {
        return id == null ? null : ownerMapper.selectById(id);
    }

    @Override
    public Map<Long, String> findNames(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return ownerMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(OwnerProfile::getId, OwnerProfile::getName, (a, b) -> a));
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String trimmed = keyword.trim();
        if (trimmed.length() > 64) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "搜索关键字过长");
        }
        return trimmed;
    }

    private String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        try {
            return OwnerType.valueOf(type.trim().toUpperCase()).name();
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "客源类型不合法");
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
