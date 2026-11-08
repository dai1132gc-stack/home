package com.example.realestate.modules.house.model.vo;

/** 房源媒体项：url 为相对路径（/uploads/...），客户端按环境拼域名 */
public record HouseMediaVO(
        Long id,
        String mediaType,
        String url,
        Integer sort
) {
}
