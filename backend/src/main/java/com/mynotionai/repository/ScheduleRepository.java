package com.mynotionai.repository;

import com.mynotionai.entity.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ScheduleRepository extends JpaRepository<Schedule, Long> {
    @Query("""
        select s from Schedule s
        where s.user.id = :userId and s.startAt >= :from and s.startAt < :to
        order by s.startAt asc
        """)
    List<Schedule> findUserSchedulesInRange(
        @Param("userId") Long userId,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to
    );
    
    List<Schedule> findByUserIdAndStartAtBetween(
        Long userId, LocalDateTime startAt, LocalDateTime endAt
    );
    
    List<Schedule> findByUserIdOrderByStartAtAsc(Long userId);

    Optional<Schedule> findByIdAndUserId(Long id, Long userId);

    List<Schedule> findByStartAtBetweenOrderByStartAtAsc(LocalDateTime startAt, LocalDateTime endAt);
}
