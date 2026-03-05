package com.mynotionai.repository;

import com.mynotionai.entity.ChatLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatLogRepository extends JpaRepository<ChatLog, Long> {
    List<ChatLog> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<ChatLog> findByUserIdOrderByCreatedAtAsc(Long userId);
}
