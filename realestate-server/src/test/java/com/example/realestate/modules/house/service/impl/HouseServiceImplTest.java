package com.example.realestate.modules.house.service.impl;

import com.example.realestate.common.api.PageResponse;
import com.example.realestate.common.enums.ErrorCode;
import com.example.realestate.common.exception.BusinessException;
import com.example.realestate.modules.house.convert.HouseConvert;
import com.example.realestate.modules.house.mapper.HouseFavoriteMapper;
import com.example.realestate.modules.house.mapper.HouseMapper;
import com.example.realestate.modules.house.mapper.HouseMediaMapper;
import com.example.realestate.modules.house.model.dto.HouseUpdateRequest;
import com.example.realestate.modules.house.model.entity.House;
import com.example.realestate.modules.house.model.query.HouseQuery;
import com.example.realestate.modules.house.model.vo.HouseDetailVO;
import com.example.realestate.modules.house.model.vo.HouseListItemVO;
import com.example.realestate.modules.owner.service.OwnerService;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("房源服务：数据权限、状态机与乐观锁")
class HouseServiceImplTest {

    @Mock
    private HouseMapper houseMapper;
    @Mock
    private HouseMediaMapper mediaMapper;
    @Mock
    private HouseFavoriteMapper favoriteMapper;
    @Mock
    private OwnerService ownerService;
    @Mock
    private CurrentUserProvider currentUser;

    private HouseServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new HouseServiceImpl(houseMapper, mediaMapper, favoriteMapper,
                ownerService, new HouseConvert(), currentUser);
    }

    private House house(String status) {
        House house = new House();
        house.setId(1L);
        house.setTitle("测试房源");
        house.setHouseType("RENT");
        house.setStatus(status);
        house.setCommunity("测试小区");
        house.setRegion("浦东新区");
        house.setAddress("测试路 1 号");
        house.setPrice(new BigDecimal("4500.00"));
        house.setArea(new BigDecimal("89.50"));
        house.setRooms(2);
        house.setBathrooms(1);
        house.setVersion(0);
        house.setCreatedAt(Instant.parse("2026-11-08T00:00:00Z"));
        house.setUpdatedAt(Instant.parse("2026-11-08T00:00:00Z"));
        return house;
    }

    @Test
    @DisplayName("游客列表：强制 status=PUBLISHED 下推 SQL")
    void guestListForcesPublishedStatus() {
        when(currentUser.isAdmin()).thenReturn(false);
        when(houseMapper.countByQuery(any(), eq("PUBLISHED"))).thenReturn(1L);
        HouseListItemVO item = new HouseListItemVO();
        item.setId(1L);
        when(houseMapper.selectPageList(any(), eq("PUBLISHED"), anyLong(), anyLong()))
                .thenReturn(List.of(item));
        when(mediaMapper.selectFirstImages(anyList())).thenReturn(List.of());

        PageResponse<HouseListItemVO> result = service.list(new HouseQuery());

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).getOwnerId()).isNull();
        verify(houseMapper).countByQuery(any(), eq("PUBLISHED"));
    }

    @Test
    @DisplayName("ADMIN 列表：不强制 status，可按状态筛选")
    void adminListDoesNotForceStatus() {
        when(currentUser.isAdmin()).thenReturn(true);
        when(houseMapper.countByQuery(any(), isNull())).thenReturn(0L);

        HouseQuery query = new HouseQuery();
        query.setStatus("pending");
        PageResponse<HouseListItemVO> result = service.list(query);

        assertThat(result.items()).isEmpty();
        assertThat(query.getStatus()).isEqualTo("PENDING");
        verify(houseMapper).countByQuery(any(), isNull());
    }

    @Test
    @DisplayName("客户读取非 PUBLISHED 房源返回 404，不泄露存在性")
    void customerCannotReadUnpublished() {
        when(currentUser.isAdmin()).thenReturn(false);
        when(houseMapper.selectById(1L)).thenReturn(house("PENDING"));

        assertThatThrownBy(() -> service.detail(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("ADMIN 可读取 PENDING 房源，且可见房东手机号")
    void adminCanReadPendingWithOwnerPhone() {
        when(currentUser.isAdmin()).thenReturn(true);
        House pending = house("PENDING");
        pending.setOwnerId(9L);
        when(houseMapper.selectById(1L)).thenReturn(pending);
        when(mediaMapper.selectList(any())).thenReturn(List.of());
        when(ownerService.findEntityOrNull(9L)).thenReturn(owner(9L));
        when(favoriteMapper.selectCount(any())).thenReturn(0L);

        HouseDetailVO detail = service.detail(1L);

        assertThat(detail.status()).isEqualTo("PENDING");
        assertThat(detail.ownerPhone()).isEqualTo("13812345678");
    }

    @Test
    @DisplayName("发布：PENDING → PUBLISHED 成功并写入状态")
    void publishFromPending() {
        when(currentUser.isAdmin()).thenReturn(true);
        when(houseMapper.selectById(1L)).thenReturn(house("PENDING"), house("PUBLISHED"));
        when(houseMapper.updateById(any(House.class))).thenReturn(1);
        when(mediaMapper.selectList(any())).thenReturn(List.of());
        when(favoriteMapper.selectCount(any())).thenReturn(0L);

        HouseDetailVO detail = service.publish(1L);

        assertThat(detail.status()).isEqualTo("PUBLISHED");
        ArgumentCaptor<House> captor = ArgumentCaptor.forClass(House.class);
        verify(houseMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("PUBLISHED");
    }

    @Test
    @DisplayName("发布：已 PUBLISHED 再发布返回 409")
    void publishTwiceConflicts() {
        when(currentUser.isAdmin()).thenReturn(true);
        when(houseMapper.selectById(1L)).thenReturn(house("PUBLISHED"));

        assertThatThrownBy(() -> service.publish(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.CONFLICT);
        ArgumentCaptor<House> captor = ArgumentCaptor.forClass(House.class);
        verify(houseMapper, never()).updateById(captor.capture());
    }

    @Test
    @DisplayName("编辑：乐观锁版本冲突返回 409")
    void updateVersionConflict() {
        when(houseMapper.selectById(1L)).thenReturn(house("PENDING"));
        when(houseMapper.updateById(any(House.class))).thenReturn(0);

        HouseUpdateRequest request = new HouseUpdateRequest("新标题", null, null, null, null,
                null, null, null, null, null, null, null, 3);

        assertThatThrownBy(() -> service.update(1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    @DisplayName("非法排序回落默认，非法类型直接 400")
    void validatesSortAndType() {
        when(currentUser.isAdmin()).thenReturn(true);
        when(houseMapper.countByQuery(any(), isNull())).thenReturn(0L);

        HouseQuery query = new HouseQuery();
        query.setSort("price; DROP TABLE house");
        service.list(query);
        assertThat(query.getSort()).isEqualTo("created_desc");

        HouseQuery illegal = new HouseQuery();
        illegal.setHouseType("BUY");
        assertThatThrownBy(() -> service.list(illegal))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    private com.example.realestate.modules.owner.model.entity.OwnerProfile owner(Long id) {
        var owner = new com.example.realestate.modules.owner.model.entity.OwnerProfile();
        owner.setId(id);
        owner.setName("房东");
        owner.setPhone("13812345678");
        return owner;
    }
}
