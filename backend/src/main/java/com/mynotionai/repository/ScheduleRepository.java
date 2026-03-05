package com.mynotionai.repository;

import com.mynotionai.entity.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ScheduleRepository extends JpaRepository<Schedule, Long> {
    List<Schedule> findByUserIdAndStartAtBetweenOrderByStartAtAsc(
        Long userId, LocalDateTime startAt, LocalDateTime endAt
    );
    
    List<Schedule> findByUserIdAndStartAtBetween(
        Long userId, LocalDateTime startAt, LocalDateTime endAt
    );
    
    List<Schedule> findByUserIdOrderByStartAtAsc(Long userId);

    Optional<Schedule> findByIdAndUserId(Long id, Long userId);
}
