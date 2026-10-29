package com.mynotionai.controller;

import com.mynotionai.config.SecurityConfig;
import com.mynotionai.exception.BusinessException;
import com.mynotionai.exception.ErrorCode;
import com.mynotionai.security.JwtTokenProvider;
import com.mynotionai.service.ScheduleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CalendarController.class)
@Import(SecurityConfig.class)
class CalendarControllerTest {

    private static final Long USER_ID = 1L;
    private static final Long MISSING_SCHEDULE_ID = 999L;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ScheduleService scheduleService;

    // JwtAuthenticationFilter가 의존하므로 컨텍스트 구성을 위해 필요
    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    private UsernamePasswordAuthenticationToken loginUser() {
        return new UsernamePasswordAuthenticationToken(USER_ID, null, Collections.emptyList());
    }

    @Test
    @DisplayName("없는 일정을 삭제하면 404와 공통 에러 포맷을 반환한다")
    void deleteMissingScheduleReturns404() throws Exception {
        willThrow(new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND))
            .given(scheduleService).deleteSchedule(USER_ID, MISSING_SCHEDULE_ID);

        mockMvc.perform(delete("/calendar/events/{id}", MISSING_SCHEDULE_ID)
                .with(authentication(loginUser())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("SCHEDULE_NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("Schedule not found"))
            .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("필수값이 빠지면 400과 필드별 에러를 반환한다")
    void missingFieldsReturn400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/calendar/events")
                .with(authentication(loginUser()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memo\":\"제목 없음\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
            .andExpect(jsonPath("$.fieldErrors.title").value("제목은 필수입니다"))
            .andExpect(jsonPath("$.fieldErrors.startAt").exists());
    }

    @Test
    @DisplayName("시간 범위가 잘못되면 서비스 예외가 400으로 변환된다")
    void invalidTimeRangeReturns400() throws Exception {
        given(scheduleService.createSchedule(anyLong(), any()))
            .willThrow(new BusinessException(ErrorCode.INVALID_TIME_RANGE));

        mockMvc.perform(post("/calendar/events")
                .with(authentication(loginUser()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title":"회의","startAt":"2026-10-28T11:00:00","endAt":"2026-10-28T10:00:00"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TIME_RANGE"));
    }

    @Test
    @DisplayName("JSON 형식이 깨지면 500이 아니라 400을 반환한다")
    void malformedJsonReturns400() throws Exception {
        mockMvc.perform(post("/calendar/events")
                .with(authentication(loginUser()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{not-json"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }
}
