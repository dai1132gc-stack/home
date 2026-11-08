package com.example.realestate.modules.house.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.realestate.common.api.PageResponse;
import com.example.realestate.common.enums.ErrorCode;
import com.example.realestate.common.enums.HouseStatus;
import com.example.realestate.common.enums.HouseType;
import com.example.realestate.common.enums.MediaType;
import com.example.realestate.common.exception.BusinessException;
import com.example.realestate.modules.house.convert.HouseConvert;
import com.example.realestate.modules.house.mapper.HouseFavoriteMapper;
import com.example.realestate.modules.house.mapper.HouseMapper;
import com.example.realestate.modules.house.mapper.HouseMediaMapper;
import com.example.realestate.modules.house.model.dto.HouseCreateRequest;
import com.example.realestate.modules.house.model.dto.HouseMediaRequest;
import com.example.realestate.modules.house.model.dto.HouseUpdateRequest;
import com.example.realestate.modules.house.model.entity.House;
import com.example.realestate.modules.house.model.entity.HouseFavorite;
import com.example.realestate.modules.house.model.entity.HouseMedia;
import com.example.realestate.modules.house.model.query.HouseQuery;
import com.example.realestate.modules.house.model.vo.HouseCover;
import com.example.realestate.modules.house.model.vo.HouseDetailVO;
import com.example.realestate.modules.house.model.vo.HouseListItemVO;
import com.example.realestate.modules.house.model.vo.HouseMediaVO;
import com.example.realestate.modules.house.service.HouseService;
import com.example.realestate.modules.owner.model.entity.OwnerProfile;
import com.example.realestate.modules.owner.service.OwnerService;
import com.example.realestate.security.CurrentUserProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 房源服务：状态机 + 数据权限 + 查询下推。
 * 数据权限规则：
 *  - ADMIN：可见全部状态，可写；
 *  - CUSTOMER / 游客：列表与详情仅 PUBLISHED，且详情不返回房东联系方式。
 * 所有列表条件（含强制 status）通过 Mapper XML 下推 SQL，不在内存过滤。
 */
@Service
public class HouseServiceImpl implements HouseService {

    private static final Logger log = LoggerFactory.getLogger(HouseServiceImpl.class);

    private static final Set<String> SORT_WHITELIST =
            Set.of("created_desc", "price_asc", "price_desc", "area_asc", "area_desc");
    private static final int MAX_MEDIA_PER_HOUSE = 20;

    private final HouseMapper houseMapper;
    private final HouseMediaMapper mediaMapper;
    private final HouseFavoriteMapper favoriteMapper;
    private final OwnerService ownerService;
    private final HouseConvert houseConvert;
    private final CurrentUserProvider currentUser;

    public HouseServiceImpl(HouseMapper houseMapper,
                            HouseMediaMapper mediaMapper,
                            HouseFavoriteMapper favoriteMapper,
                            OwnerService ownerService,
                            HouseConvert houseConvert,
                            CurrentUserProvider currentUser) {
        this.houseMapper = houseMapper;
        this.mediaMapper = mediaMapper;
        this.favoriteMapper = favoriteMapper;
        this.ownerService = ownerService;
        this.houseConvert = houseConvert;
        this.currentUser = currentUser;
    }

    @Override
    public PageResponse<HouseListItemVO> list(HouseQuery query) {
        HouseQuery safe = query == null ? new HouseQuery() : query;
        boolean admin = currentUser.isAdmin();

        safe.setRegion(trimToNull(safe.getRegion()));
        safe.setKeyword(trimToNull(safe.getKeyword()));
        safe.setStatus(normalizeStatus(safe.getStatus()));
        safe.setHouseType(normalizeHouseType(safe.getHouseType()));
        safe.setSort(normalizeSort(safe.getSort()));
        validateRange(safe.getMinPrice(), safe.getMaxPrice(), "价格");
        validateRange(safe.getMinArea(), safe.getMaxArea(), "面积");
        if (safe.getKeyword() != null && safe.getKeyword().length() > 100) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "搜索关键字过长");
        }

        // 数据权限：非 ADMIN（含游客）强制只查已发布，条件直接进入 SQL
        String forcedStatus = admin ? null : HouseStatus.PUBLISHED.name();
        long total = houseMapper.countByQuery(safe, forcedStatus);
        long size = safe.normalizedSize();
        if (total == 0) {
            return PageResponse.of(List.of(), 0, safe.normalizedPage(), size);
        }

        List<HouseListItemVO> items = houseMapper.selectPageList(safe, forcedStatus, safe.offset(), size);
        fillCovers(items);
        if (admin) {
            fillOwnerNames(items);
        } else {
            // 非 ADMIN 不返回房东信息（含 ownerId），避免暴露内部关联
            items.forEach(i -> {
                i.setOwnerId(null);
                i.setOwnerName(null);
            });
        }
        return PageResponse.of(items, total, safe.normalizedPage(), size);
    }

    @Override
    public HouseDetailVO detail(Long id) {
        House house = requireVisibleHouse(id);
        return buildDetail(house, currentUser.isAdmin());
    }

    @Override
    @Transactional
    public HouseDetailVO create(HouseCreateRequest request) {
        validateOwner(request.ownerId());
        Instant now = Instant.now();
        House entity = new House();
        entity.setTitle(request.title().trim());
        entity.setHouseType(request.houseType().name());
        entity.setStatus(HouseStatus.PENDING.name());
        entity.setCommunity(request.community().trim());
        entity.setRegion(request.region().trim());
        entity.setAddress(request.address().trim());
        entity.setPrice(request.price());
        entity.setArea(request.area());
        entity.setRooms(request.rooms());
        entity.setBathrooms(request.bathrooms());
        entity.setFloor(trimToNull(request.floor()));
        entity.setDescription(trimToNull(request.description()));
        entity.setOwnerId(request.ownerId());
        entity.setVersion(0);
        entity.setDeleted(0);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        houseMapper.insert(entity);
        log.info("房源创建成功 id={} operator={}", entity.getId(), currentUser.currentUserIdOrNull());
        return buildDetail(entity, true);
    }

    @Override
    @Transactional
    public HouseDetailVO update(Long id, HouseUpdateRequest request) {
        House existing = requireHouse(id);
        House update = new House();
        update.setId(id);
        if (request.title() != null && !request.title().isBlank()) {
            update.setTitle(request.title().trim());
        }
        if (request.houseType() != null) {
            update.setHouseType(request.houseType().name());
        }
        if (request.community() != null && !request.community().isBlank()) {
            update.setCommunity(request.community().trim());
        }
        if (request.region() != null && !request.region().isBlank()) {
            update.setRegion(request.region().trim());
        }
        if (request.address() != null && !request.address().isBlank()) {
            update.setAddress(request.address().trim());
        }
        if (request.price() != null) {
            update.setPrice(request.price());
        }
        if (request.area() != null) {
            update.setArea(request.area());
        }
        if (request.rooms() != null) {
            update.setRooms(request.rooms());
        }
        if (request.bathrooms() != null) {
            update.setBathrooms(request.bathrooms());
        }
        if (request.floor() != null) {
            update.setFloor(trimToNull(request.floor()));
        }
        if (request.description() != null) {
            update.setDescription(trimToNull(request.description()));
        }
        if (request.ownerId() != null) {
            validateOwner(request.ownerId());
            update.setOwnerId(request.ownerId());
        }
        update.setVersion(request.version() != null ? request.version() : existing.getVersion());
        update.setUpdatedAt(Instant.now());

        int rows = houseMapper.updateById(update);
        if (rows == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "房源已被他人修改，请刷新后重试");
        }
        return buildDetail(houseMapper.selectById(id), true);
    }

    @Override
    @Transactional
    public HouseDetailVO publish(Long id) {
        return changeStatus(id, HouseStatus.PUBLISHED);
    }

    @Override
    @Transactional
    public HouseDetailVO offline(Long id) {
        return changeStatus(id, HouseStatus.OFFLINE);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        requireHouse(id);
        houseMapper.deleteById(id);
        log.info("房源逻辑删除 id={} operator={}", id, currentUser.currentUserIdOrNull());
    }

    @Override
    @Transactional
    public HouseMediaVO addMedia(Long houseId, HouseMediaRequest request) {
        requireHouse(houseId);
        String url = normalizeMediaUrl(request.url());
        long existing = mediaMapper.selectCount(
                Wrappers.<HouseMedia>lambdaQuery().eq(HouseMedia::getHouseId, houseId));
        if (existing >= MAX_MEDIA_PER_HOUSE) {
            throw new BusinessException(ErrorCode.CONFLICT, "单个房源媒体数量已达上限 " + MAX_MEDIA_PER_HOUSE);
        }
        HouseMedia media = new HouseMedia();
        media.setHouseId(houseId);
        media.setMediaType((request.mediaType() == null ? MediaType.IMAGE : request.mediaType()).name());
        media.setUrl(url);
        media.setSort(request.sort() == null ? 0 : request.sort());
        media.setCreatedAt(Instant.now());
        mediaMapper.insert(media);
        return houseConvert.toMediaVO(media);
    }

    @Override
    @Transactional
    public void removeMedia(Long houseId, Long mediaId) {
        HouseMedia media = mediaId == null ? null : mediaMapper.selectById(mediaId);
        if (media == null || !houseId.equals(media.getHouseId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "媒体不存在");
        }
        mediaMapper.deleteById(mediaId);
    }

    // ---------- 内部方法 ----------

    /** 状态迁移：校验状态机 → 乐观锁更新 → 冲突返回 409 */
    private HouseDetailVO changeStatus(Long id, HouseStatus target) {
        House house = requireHouse(id);
        HouseStatus current = HouseStatus.of(house.getStatus());
        if (current == null || !current.canTransitionTo(target)) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "房源当前状态 " + house.getStatus() + " 不允许变更为 " + target.name());
        }
        House update = new House();
        update.setId(id);
        update.setStatus(target.name());
        update.setVersion(house.getVersion());
        update.setUpdatedAt(Instant.now());
        int rows = houseMapper.updateById(update);
        if (rows == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "房源已被他人修改，请刷新后重试");
        }
        log.info("房源状态变更 id={} {} -> {} operator={}", id, current, target,
                currentUser.currentUserIdOrNull());
        return buildDetail(houseMapper.selectById(id), true);
    }

    private HouseDetailVO buildDetail(House house, boolean admin) {
        List<HouseMedia> media = mediaMapper.selectList(
                Wrappers.<HouseMedia>lambdaQuery()
                        .eq(HouseMedia::getHouseId, house.getId())
                        .orderByAsc(HouseMedia::getSort)
                        .orderByAsc(HouseMedia::getId));

        String ownerName = null;
        String ownerPhone = null;
        if (admin && house.getOwnerId() != null) {
            OwnerProfile owner = ownerService.findEntityOrNull(house.getOwnerId());
            if (owner != null) {
                ownerName = owner.getName();
                ownerPhone = owner.getPhone();
            }
        }
        boolean favorited = false;
        Long userId = currentUser.currentUserIdOrNull();
        if (userId != null) {
            favorited = favoriteMapper.selectCount(Wrappers.<HouseFavorite>lambdaQuery()
                    .eq(HouseFavorite::getHouseId, house.getId())
                    .eq(HouseFavorite::getUserId, userId)) > 0;
        }
        return houseConvert.toDetail(house, media, ownerName, ownerPhone, favorited);
    }

    private House requireHouse(Long id) {
        House house = id == null ? null : houseMapper.selectById(id);
        if (house == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "房源不存在");
        }
        return house;
    }

    /** 非 PUBLISHED 的房源对非 ADMIN 一律 404，不泄露资源是否存在 */
    private House requireVisibleHouse(Long id) {
        House house = id == null ? null : houseMapper.selectById(id);
        boolean visible = house != null
                && (currentUser.isAdmin() || HouseStatus.PUBLISHED.name().equals(house.getStatus()));
        if (!visible) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "房源不存在");
        }
        return house;
    }

    private void fillCovers(List<HouseListItemVO> items) {
        if (items.isEmpty()) {
            return;
        }
        List<Long> ids = items.stream().map(HouseListItemVO::getId).toList();
        Map<Long, String> covers = mediaMapper.selectFirstImages(ids).stream()
                .collect(Collectors.toMap(HouseCover::getHouseId, HouseCover::getUrl, (a, b) -> a));
        items.forEach(i -> i.setCoverUrl(covers.get(i.getId())));
    }

    private void fillOwnerNames(List<HouseListItemVO> items) {
        Set<Long> ownerIds = items.stream()
                .map(HouseListItemVO::getOwnerId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (ownerIds.isEmpty()) {
            return;
        }
        Map<Long, String> names = ownerService.findNames(ownerIds);
        items.forEach(i -> i.setOwnerName(names.get(i.getOwnerId())));
    }

    private void validateOwner(Long ownerId) {
        if (ownerId != null) {
            ownerService.requireEntity(ownerId);
        }
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        HouseStatus parsed = HouseStatus.of(status);
        if (parsed == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "房源状态不合法");
        }
        return parsed.name();
    }

    private String normalizeHouseType(String houseType) {
        if (houseType == null || houseType.isBlank()) {
            return null;
        }
        try {
            return HouseType.valueOf(houseType.trim().toUpperCase()).name();
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "房源类型不合法");
        }
    }

    /** 排序白名单：非法值回落默认，绝不把客户端字符串拼入 SQL */
    private String normalizeSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return "created_desc";
        }
        String normalized = sort.trim().toLowerCase();
        return SORT_WHITELIST.contains(normalized) ? normalized : "created_desc";
    }

    private void validateRange(BigDecimal min, BigDecimal max, String name) {
        if (min != null && min.signum() < 0 || max != null && max.signum() < 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, name + "不能为负数");
        }
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, name + "区间不合法");
        }
    }

    private String normalizeMediaUrl(String url) {
        String trimmed = url == null ? "" : url.trim();
        boolean relative = trimmed.startsWith("/") && !trimmed.contains("..");
        boolean remote = trimmed.startsWith("https://") && !trimmed.contains("..");
        if (trimmed.isEmpty() || trimmed.length() > 500 || !(relative || remote)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "媒体地址不合法");
        }
        return trimmed;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
