package com.example.realestate.modules.ai.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 政策问答请求（W6）：question ≤200 字。 */
public record PolicyAnswerRequest(
        @NotBlank @Size(max = 200) String question
) {
}
