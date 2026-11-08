package com.example.realestate.modules.house.model.dto;

import com.example.realestate.common.enums.MediaType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 关联媒体到房源（上传由 /files/upload 完成，此处只保存引用） */
@Schema(description = "房源媒体关联请求")
public record HouseMediaRequest(

        @NotBlank(message = "媒体地址不能为空")
        @Size(max = 500, message = "媒体地址不能超过 500 字")
        String url,

        MediaType mediaType,

        @Min(value = 0, message = "排序不能小于 0")
        Integer sort
) {
}
