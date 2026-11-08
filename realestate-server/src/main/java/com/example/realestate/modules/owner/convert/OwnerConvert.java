package com.example.realestate.modules.owner.convert;

import com.example.realestate.common.enums.OwnerType;
import com.example.realestate.common.util.PhoneMasker;
import com.example.realestate.modules.owner.model.entity.OwnerProfile;
import com.example.realestate.modules.owner.model.vo.OwnerVO;
import org.springframework.stereotype.Component;

/**
 * 客源实体 → VO。
 * 手写而非 MapStruct：phone 需按调用方角色脱敏，属于有分支的展示逻辑，
 * 用 MapStruct 反而需要 Expression 注入，可读性与可测试性更差。
 */
@Component
public class OwnerConvert {

    public OwnerVO toVO(OwnerProfile entity, boolean admin) {
        if (entity == null) {
            return null;
        }
        return new OwnerVO(
                entity.getId(),
                entity.getName(),
                admin ? entity.getPhone() : PhoneMasker.mask(entity.getPhone()),
                entity.getType(),
                typeLabel(entity.getType()),
                entity.getRemark(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private String typeLabel(String type) {
        if (type == null) {
            return null;
        }
        try {
            return OwnerType.valueOf(type).label();
        } catch (IllegalArgumentException e) {
            return type;
        }
    }
}
