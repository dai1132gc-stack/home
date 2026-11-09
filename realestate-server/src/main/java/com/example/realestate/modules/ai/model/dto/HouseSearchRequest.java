package com.example.realestate.modules.ai.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 自然语言选房请求（W6）：query ≤200 字，size ≤100（与 PageQuery 上限一致）。 */
public record HouseSearchRequest(
        @NotBlank @Size(max = 200) String query,
        @Min(1) Integer page,
        @Min(1) @Max(100) Integer size
) {
}
