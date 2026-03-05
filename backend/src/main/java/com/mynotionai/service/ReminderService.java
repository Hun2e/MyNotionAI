package com.mynotionai.service;

import com.mynotionai.entity.ChatLog;
import com.mynotionai.entity.ReminderLog;
import com.mynotionai.entity.Schedule;
import com.mynotionai.repository.ChatLogRepository;
import com.mynotionai.repository.ReminderLogRepository;
import com.mynotionai.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReminderService {

    private final ScheduleRepository scheduleRepository;
    private final ReminderLogRepository reminderLogRepository;
    private final ChatLogRepository chatLogRepository;

    @Transactional
    public void sendOneHourReminders() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from = now.plusMinutes(59);
        LocalDateTime to = now.plusMinutes(61);

        List<Schedule> upcomingSchedules = scheduleRepository.findByStartAtBetweenOrderByStartAtAsc(from, to);

        for (Schedule schedule : upcomingSchedules) {
            if (reminderLogRepository.existsByScheduleId(schedule.getId())) {
                continue;
            }

            ReminderLog reminderLog = ReminderLog.builder()
                .user(schedule.getUser())
                .schedule(schedule)
                .remindAt(now)
                .scheduleStartAt(schedule.getStartAt())
                .build();
            reminderLogRepository.save(reminderLog);

            ChatLog reminderMessage = ChatLog.builder()
                .user(schedule.getUser())
                .role(ChatLog.Role.SYSTEM)
                .content("[리마인드] '" + schedule.getTitle() + "' 일정이 1시간 후에 시작됩니다.")
                .build();
            chatLogRepository.save(reminderMessage);

            log.info("Reminder sent: scheduleId={}, userId={}", schedule.getId(), schedule.getUser().getId());
        }
    }
}
