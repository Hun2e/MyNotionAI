package com.mynotionai.repository;

import com.mynotionai.entity.AiAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AiActionRepository extends JpaRepository<AiAction, Long> {
    List<AiAction> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<AiAction> findByTargetScheduleId(Long scheduleId);
}
