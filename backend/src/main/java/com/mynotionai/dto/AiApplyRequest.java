package com.mynotionai.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiApplyRequest {

    @Valid
    @NotNull(message = "draft is required")
    private AiScheduleDraft draft;
}
