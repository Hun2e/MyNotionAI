package com.mynotionai.repository;

import com.mynotionai.entity.ReminderLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReminderLogRepository extends JpaRepository<ReminderLog, Long> {
    boolean existsByScheduleId(Long scheduleId);
}
