package com.example.realestate.modules.house.model.vo;

import lombok.Data;
import lombok.NoArgsConstructor;

/** 房源封面行（批量查询首页图片用，避免 N+1） */
@Data
@NoArgsConstructor
public class HouseCover {

    private Long houseId;

    private String url;
}
