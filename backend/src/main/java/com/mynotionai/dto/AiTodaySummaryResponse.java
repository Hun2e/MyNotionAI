package com.mynotionai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiTodaySummaryResponse {
    private String summary;
    private int totalCount;
    private List<ScheduleResponse> schedules;
}
