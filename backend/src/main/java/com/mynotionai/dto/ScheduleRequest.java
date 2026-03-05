package com.mynotionai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleRequest {
    
    @NotBlank(message = "제목은 필수입니다")
    private String title;
    
    @NotNull(message = "시작 시간은 필수입니다")
    private LocalDateTime startAt;
    
    @NotNull(message = "종료 시간은 필수입니다")
    private LocalDateTime endAt;
    
    private String memo;
}
