package com.mynotionai.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiCancelRequest {

    @Valid
    @NotNull(message = "draft is required")
    private AiScheduleDraft draft;

    @NotBlank(message = "reason is required")
    private String reason;
}
