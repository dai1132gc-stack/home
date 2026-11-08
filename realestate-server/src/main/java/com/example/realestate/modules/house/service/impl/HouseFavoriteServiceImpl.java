package com.example.realestate.modules.house.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.realestate.common.api.PageQuery;
import com.example.realestate.common.api.PageResponse;
import com.example.realestate.common.enums.ErrorCode;
import com.example.realestate.common.enums.HouseStatus;
import com.example.realestate.common.exception.BusinessException;
import com.example.realestate.modules.house.mapper.HouseFavoriteMapper;
import com.example.realestate.modules.house.mapper.HouseMapper;
import com.example.realestate.modules.house.mapper.HouseMediaMapper;
import com.example.realestate.modules.house.model.entity.House;
import com.example.realestate.modules.house.model.entity.HouseFavorite;
import com.example.realestate.modules.house.model.vo.FavoriteVO;
import com.example.realestate.modules.house.model.vo.HouseCover;
import com.example.realestate.modules.house.service.HouseFavoriteService;
import com.example.realestate.security.CurrentUserProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 收藏服务：user_id 始终取自登录态，客户端无法指定他人 user_id。
 * 唯一键 uk_house_favorite_user_house 保证并发重复收藏不产生脏数据，此处做幂等处理。
 */
@Service
public class HouseFavoriteServiceImpl implements HouseFavoriteService {

    private static final Logger log = LoggerFactory.getLogger(HouseFavoriteServiceImpl.class);

    private final HouseFavoriteMapper favoriteMapper;
    private final HouseMapper houseMapper;
    private final HouseMediaMapper mediaMapper;
    private final CurrentUserProvider currentUser;

    public HouseFavoriteServiceImpl(HouseFavoriteMapper favoriteMapper,
                                    HouseMapper houseMapper,
                                    HouseMediaMapper mediaMapper,
                                    CurrentUserProvider currentUser) {
        this.favoriteMapper = favoriteMapper;
        this.houseMapper = houseMapper;
        this.mediaMapper = mediaMapper;
        this.currentUser = currentUser;
    }

    @Override
    @Transactional
    public void add(Long houseId) {
        Long userId = currentUser.requireUserId();
        House house = houseId == null ? null : houseMapper.selectById(houseId);
        if (house == null || !HouseStatus.PUBLISHED.name().equals(house.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "房源不存在或未发布");
        }
        HouseFavorite favorite = new HouseFavorite();
        favorite.setHouseId(houseId);
        favorite.setUserId(userId);
        favorite.setCreatedAt(Instant.now());
        try {
            favoriteMapper.insert(favorite);
            log.info("收藏成功 houseId={} userId={}", houseId, userId);
        } catch (DuplicateKeyException e) {
            // 并发或重复点击：唯一键兜底，按幂等成功处理
            log.debug("重复收藏已忽略 houseId={} userId={}", houseId, userId);
        }
    }

    @Override
    @Transactional
    public void remove(Long houseId) {
        Long userId = currentUser.requireUserId();
        // WHERE user_id = 当前登录用户，天然无法删除他人收藏
        int rows = favoriteMapper.delete(Wrappers.<HouseFavorite>lambdaQuery()
                .eq(HouseFavorite::getHouseId, houseId)
                .eq(HouseFavorite::getUserId, userId));
        log.info("取消收藏 houseId={} userId={} affected={}", houseId, userId, rows);
    }

    @Override
    public PageResponse<FavoriteVO> myFavorites(PageQuery query) {
        Long userId = currentUser.requireUserId();
        PageQuery safe = query == null ? new PageQuery() : query;
        long size = safe.normalizedSize();
        long total = favoriteMapper.countFavorites(userId);
        if (total == 0) {
            return PageResponse.of(List.of(), 0, safe.normalizedPage(), size);
        }
        List<FavoriteVO> items = favoriteMapper.selectFavoritePage(userId, safe.offset(), size);
        if (!items.isEmpty()) {
            List<Long> houseIds = items.stream().map(FavoriteVO::getHouseId).toList();
            Map<Long, String> covers = mediaMapper.selectFirstImages(houseIds).stream()
                    .collect(Collectors.toMap(HouseCover::getHouseId, HouseCover::getUrl, (a, b) -> a));
            items.forEach(i -> i.setCoverUrl(covers.get(i.getHouseId())));
        }
        return PageResponse.of(items, total, safe.normalizedPage(), size);
    }
}
