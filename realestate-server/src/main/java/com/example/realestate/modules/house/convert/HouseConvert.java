package com.example.realestate.modules.house.convert;

import com.example.realestate.common.enums.HouseType;
import com.example.realestate.modules.house.model.entity.House;
import com.example.realestate.modules.house.model.entity.HouseMedia;
import com.example.realestate.modules.house.model.vo.HouseDetailVO;
import com.example.realestate.modules.house.model.vo.HouseListItemVO;
import com.example.realestate.modules.house.model.vo.HouseMediaVO;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 房源实体 → VO。
 * 手写而非 MapStruct：详情需要按角色裁剪 owner 字段、拼接媒体与收藏状态，
 * 属于带数据权限的组装逻辑，MapStruct 生成代码反而更难审查。
 */
@Component
public class HouseConvert {

    public HouseListItemVO toListItem(House house) {
        if (house == null) {
            return null;
        }
        HouseListItemVO vo = new HouseListItemVO();
        vo.setId(house.getId());
        vo.setTitle(house.getTitle());
        vo.setHouseType(house.getHouseType());
        vo.setStatus(house.getStatus());
        vo.setCommunity(house.getCommunity());
        vo.setRegion(house.getRegion());
        vo.setPrice(house.getPrice());
        vo.setArea(house.getArea());
        vo.setRooms(house.getRooms());
        vo.setBathrooms(house.getBathrooms());
        vo.setFloor(house.getFloor());
        vo.setCreatedAt(house.getCreatedAt());
        return vo;
    }

    public HouseDetailVO toDetail(House house,
                                  List<HouseMedia> media,
                                  String ownerName,
                                  String ownerPhone,
                                  boolean favorited) {
        if (house == null) {
            return null;
        }
        List<HouseMediaVO> mediaVOs = media == null ? List.of() : media.stream().map(this::toMediaVO).toList();
        String cover = mediaVOs.stream()
                .filter(m -> "IMAGE".equals(m.mediaType()))
                .map(HouseMediaVO::url)
                .findFirst()
                .orElse(null);
        return new HouseDetailVO(
                house.getId(),
                house.getTitle(),
                house.getHouseType(),
                house.getStatus(),
                house.getCommunity(),
                house.getRegion(),
                house.getAddress(),
                house.getPrice(),
                house.getArea(),
                house.getRooms(),
                house.getBathrooms(),
                house.getFloor(),
                house.getDescription(),
                cover,
                mediaVOs,
                ownerName,
                ownerPhone,
                house.getVersion(),
                house.getCreatedAt(),
                house.getUpdatedAt(),
                favorited);
    }

    public HouseMediaVO toMediaVO(HouseMedia media) {
        return new HouseMediaVO(media.getId(), media.getMediaType(), media.getUrl(), media.getSort());
    }

    public String typeLabel(String houseType) {
        if (houseType == null) {
            return null;
        }
        try {
            return HouseType.valueOf(houseType).label();
        } catch (IllegalArgumentException e) {
            return houseType;
        }
    }
}
