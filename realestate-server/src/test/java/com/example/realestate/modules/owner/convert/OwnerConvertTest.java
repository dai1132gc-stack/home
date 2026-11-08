package com.example.realestate.modules.owner.convert;

import com.example.realestate.modules.owner.model.entity.OwnerProfile;
import com.example.realestate.modules.owner.model.vo.OwnerVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("客源 VO 脱敏")
class OwnerConvertTest {

    private final OwnerConvert convert = new OwnerConvert();

    private OwnerProfile owner() {
        OwnerProfile entity = new OwnerProfile();
        entity.setId(7L);
        entity.setName("张三");
        entity.setPhone("13812345678");
        entity.setType("LANDLORD");
        entity.setRemark("备注");
        entity.setCreatedAt(Instant.parse("2026-11-08T00:00:00Z"));
        entity.setUpdatedAt(Instant.parse("2026-11-08T00:00:00Z"));
        return entity;
    }

    @Test
    @DisplayName("ADMIN 返回明文手机号")
    void adminSeesPlainPhone() {
        OwnerVO vo = convert.toVO(owner(), true);
        assertThat(vo.phone()).isEqualTo("13812345678");
        assertThat(vo.typeLabel()).isEqualTo("房东");
    }

    @Test
    @DisplayName("非 ADMIN 手机号脱敏")
    void nonAdminSeesMaskedPhone() {
        OwnerVO vo = convert.toVO(owner(), false);
        assertThat(vo.phone()).isEqualTo("138******78");
    }

    @Test
    @DisplayName("未知类型保留原值，null 实体返回 null")
    void handlesUnknownType() {
        OwnerProfile entity = owner();
        entity.setType("UNKNOWN");
        assertThat(convert.toVO(entity, true).typeLabel()).isEqualTo("UNKNOWN");
        assertThat(convert.toVO(null, true)).isNull();
    }
}
