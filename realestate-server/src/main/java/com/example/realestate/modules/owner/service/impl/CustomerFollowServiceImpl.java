package com.example.realestate.modules.owner.service.impl;

import com.example.realestate.common.api.PageQuery;
import com.example.realestate.common.api.PageResponse;
import com.example.realestate.common.enums.ErrorCode;
import com.example.realestate.common.exception.BusinessException;
import com.example.realestate.modules.owner.mapper.CustomerFollowMapper;
import com.example.realestate.modules.owner.model.dto.CustomerFollowCreateRequest;
import com.example.realestate.modules.owner.model.entity.CustomerFollow;
import com.example.realestate.modules.owner.model.vo.CustomerFollowRow;
import com.example.realestate.modules.owner.model.vo.CustomerFollowVO;
import com.example.realestate.modules.owner.service.CustomerFollowService;
import com.example.realestate.modules.owner.service.OwnerService;
import com.example.realestate.security.CurrentUserProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 客源跟进实现：owner 必须存在；分页与按 owner 过滤全部下推 SQL。
 * 复用 V2 已建的 customer_follow 表与实体，不修改表结构。
 */
@Service
public class CustomerFollowServiceImpl implements CustomerFollowService {

    private static final Logger log = LoggerFactory.getLogger(CustomerFollowServiceImpl.class);
    private static final Set<String> FOLLOW_TYPES = Set.of("PHONE", "WECHAT", "VISIT", "OTHER");
    private static final Map<String, String> FOLLOW_LABELS = Map.of(
            "PHONE", "电话", "WECHAT", "微信", "VISIT", "到访", "OTHER", "其他");

    private final CustomerFollowMapper followMapper;
    private final OwnerService ownerService;
    private final CurrentUserProvider currentUser;

    public CustomerFollowServiceImpl(CustomerFollowMapper followMapper,
                                     OwnerService ownerService,
                                     CurrentUserProvider currentUser) {
        this.followMapper = followMapper;
        this.ownerService = ownerService;
        this.currentUser = currentUser;
    }

    @Override
    public PageResponse<CustomerFollowVO> list(Long ownerId, PageQuery query) {
        ownerService.requireEntity(ownerId);
        PageQuery safe = query == null ? new PageQuery() : query;
        long total = followMapper.countByOwner(ownerId);
        long size = safe.normalizedSize();
        if (total == 0) {
            return PageResponse.of(List.of(), 0, safe.normalizedPage(), size);
        }
        List<CustomerFollowVO> items = followMapper
                .selectPageList(ownerId, safe.offset(), size)
                .stream()
                .map(this::fillLabel)
                .toList();
        return PageResponse.of(items, total, safe.normalizedPage(), size);
    }

    @Override
    @Transactional
    public CustomerFollowVO create(Long ownerId, CustomerFollowCreateRequest request) {
        ownerService.requireEntity(ownerId);
        String followType = request.followType() == null || request.followType().isBlank()
                ? "PHONE" : request.followType().trim().toUpperCase();
        if (!FOLLOW_TYPES.contains(followType)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "跟进方式不合法");
        }
        CustomerFollow entity = new CustomerFollow();
        entity.setOwnerId(ownerId);
        entity.setContent(request.content().trim());
        entity.setFollowType(followType);
        entity.setNextFollowAt(request.nextFollowAt());
        entity.setCreatedBy(currentUser.currentUserIdOrNull());
        entity.setCreatedAt(Instant.now());
        followMapper.insert(entity);
        log.info("客源跟进新增 ownerId={} followId={} type={} operator={}",
                ownerId, entity.getId(), followType, currentUser.currentUserIdOrNull());
        return fillLabel(followMapper.selectVoById(entity.getId()));
    }

    private CustomerFollowVO fillLabel(CustomerFollowRow row) {
        if (row == null) {
            return null;
        }
        return new CustomerFollowVO(row.getId(), row.getOwnerId(), row.getContent(), row.getFollowType(),
                FOLLOW_LABELS.getOrDefault(row.getFollowType(), row.getFollowType()),
                row.getNextFollowAt(), row.getCreatedBy(), row.getCreatedByName(), row.getCreatedAt());
    }
}
