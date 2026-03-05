package com.mynotionai.dto;

import com.mynotionai.entity.Schedule;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleResponse {
    private Long id;
    private String title;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private String memo;
    private Schedule.CreatedBy createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    public static ScheduleResponse from(Schedule schedule) {
        return ScheduleResponse.builder()
            .id(schedule.getId())
            .title(schedule.getTitle())
            .startAt(schedule.getStartAt())
            .endAt(schedule.getEndAt())
            .memo(schedule.getMemo())
            .createdBy(schedule.getCreatedBy())
            .createdAt(schedule.getCreatedAt())
            .updatedAt(schedule.getUpdatedAt())
            .build();
    }
}
