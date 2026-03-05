package com.mynotionai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiScheduleDraft {
    private String title;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private String memo;
}
