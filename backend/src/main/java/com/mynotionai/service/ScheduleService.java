package com.mynotionai.service;

import com.mynotionai.dto.ScheduleRequest;
import com.mynotionai.dto.ScheduleResponse;
import com.mynotionai.entity.Schedule;
import com.mynotionai.entity.User;
import com.mynotionai.exception.BusinessException;
import com.mynotionai.exception.ErrorCode;
import com.mynotionai.repository.ScheduleRepository;
import com.mynotionai.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<ScheduleResponse> getMonthlySchedules(Long userId, int year, int month) {
        if (month < 1 || month > 12) {
            throw new BusinessException(ErrorCode.INVALID_MONTH);
        }

        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDateTime from = yearMonth.atDay(1).atStartOfDay();
        LocalDateTime to = yearMonth.plusMonths(1).atDay(1).atStartOfDay();

        return scheduleRepository.findUserSchedulesInRange(userId, from, to)
            .stream()
            .map(ScheduleResponse::from)
            .toList();
    }

    @Transactional
    public ScheduleResponse createSchedule(Long userId, ScheduleRequest request) {
        validateTimeRange(request);
        User user = getUser(userId);

        Schedule schedule = Schedule.builder()
            .user(user)
            .title(request.getTitle())
            .startAt(request.getStartAt())
            .endAt(request.getEndAt())
            .memo(request.getMemo())
            .createdBy(Schedule.CreatedBy.USER)
            .build();

        return ScheduleResponse.from(scheduleRepository.save(schedule));
    }

    @Transactional
    public ScheduleResponse updateSchedule(Long userId, Long scheduleId, ScheduleRequest request) {
        validateTimeRange(request);
        Schedule schedule = getUserSchedule(userId, scheduleId);

        schedule.setTitle(request.getTitle());
        schedule.setStartAt(request.getStartAt());
        schedule.setEndAt(request.getEndAt());
        schedule.setMemo(request.getMemo());

        return ScheduleResponse.from(scheduleRepository.save(schedule));
    }

    @Transactional
    public void deleteSchedule(Long userId, Long scheduleId) {
        Schedule schedule = getUserSchedule(userId, scheduleId);
        scheduleRepository.delete(schedule);
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private Schedule getUserSchedule(Long userId, Long scheduleId) {
        return scheduleRepository.findByIdAndUserId(scheduleId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND));
    }

    private void validateTimeRange(ScheduleRequest request) {
        if (!request.getStartAt().isBefore(request.getEndAt())) {
            throw new BusinessException(ErrorCode.INVALID_TIME_RANGE);
        }
    }
}
