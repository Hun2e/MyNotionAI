package com.mynotionai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiAnalyzeResponse {

    public enum ResultType {
        PROPOSAL, NEEDS_CLARIFICATION
    }

    private ResultType resultType;
    private String message;
    private AiScheduleDraft draft;
}
