package com.example.realestate.modules.auth.convert;

import com.example.realestate.modules.auth.model.vo.UserVO;
import com.example.realestate.modules.user.model.entity.SysUser;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Entity → VO 转换（MapStruct 自动生成实现）。
 */
@Mapper(componentModel = "spring")
public interface UserConvert {

    @Mapping(target = "createdAt", source = "createdAt")
    UserVO toUserVO(SysUser user);
}
