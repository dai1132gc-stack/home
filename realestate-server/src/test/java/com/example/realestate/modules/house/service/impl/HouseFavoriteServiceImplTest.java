package com.example.realestate.modules.house.service.impl;

import com.example.realestate.common.api.PageQuery;
import com.example.realestate.common.api.PageResponse;
import com.example.realestate.common.enums.ErrorCode;
import com.example.realestate.common.exception.BusinessException;
import com.example.realestate.modules.house.mapper.HouseFavoriteMapper;
import com.example.realestate.modules.house.mapper.HouseMapper;
import com.example.realestate.modules.house.mapper.HouseMediaMapper;
import com.example.realestate.modules.house.model.entity.House;
import com.example.realestate.modules.house.model.entity.HouseFavorite;
import com.example.realestate.modules.house.model.vo.FavoriteVO;
import com.example.realestate.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("收藏服务：归属校验与幂等")
class HouseFavoriteServiceImplTest {

    @Mock
    private HouseFavoriteMapper favoriteMapper;
    @Mock
    private HouseMapper houseMapper;
    @Mock
    private HouseMediaMapper mediaMapper;
    @Mock
    private CurrentUserProvider currentUser;

    private HouseFavoriteServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new HouseFavoriteServiceImpl(favoriteMapper, houseMapper, mediaMapper, currentUser);
    }

    private House house(String status) {
        House house = new House();
        house.setId(5L);
        house.setStatus(status);
        return house;
    }

    @Test
    @DisplayName("收藏未发布房源返回 404")
    void cannotFavoriteOfflineHouse() {
        when(currentUser.requireUserId()).thenReturn(2L);
        when(houseMapper.selectById(5L)).thenReturn(house("OFFLINE"));

        assertThatThrownBy(() -> service.add(5L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("重复收藏幂等成功（唯一键兜底）")
    void duplicateFavoriteIsIdempotent() {
        when(currentUser.requireUserId()).thenReturn(2L);
        when(houseMapper.selectById(5L)).thenReturn(house("PUBLISHED"));
        when(favoriteMapper.insert(any(HouseFavorite.class)))
                .thenThrow(new DuplicateKeyException("uk_house_favorite_user_house"));

        assertThatCode(() -> service.add(5L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("收藏时 user_id 取自登录态，不使用客户端入参")
    void favoriteUsesLoginUserId() {
        when(currentUser.requireUserId()).thenReturn(42L);
        when(houseMapper.selectById(5L)).thenReturn(house("PUBLISHED"));
        when(favoriteMapper.insert(any(HouseFavorite.class))).thenReturn(1);

        service.add(5L);

        verify(currentUser).requireUserId();
        verify(favoriteMapper).insert(any(HouseFavorite.class));
    }

    @Test
    @DisplayName("取消收藏按 user_id + house_id 删除，无法影响他人")
    void removeScopedToCurrentUser() {
        when(currentUser.requireUserId()).thenReturn(42L);
        when(favoriteMapper.delete(any())).thenReturn(1);

        service.remove(5L);

        verify(currentUser).requireUserId();
        verify(favoriteMapper).delete(any());
    }

    @Test
    @DisplayName("收藏列表为空时不查封面")
    void emptyFavoritesSkipsCoverQuery() {
        when(currentUser.requireUserId()).thenReturn(42L);
        when(favoriteMapper.countFavorites(42L)).thenReturn(0L);

        PageResponse<FavoriteVO> result = service.myFavorites(new PageQuery());

        assertThat(result.items()).isEmpty();
        assertThat(result.total()).isZero();
        verify(mediaMapper, org.mockito.Mockito.never()).selectFirstImages(any());
    }
}
