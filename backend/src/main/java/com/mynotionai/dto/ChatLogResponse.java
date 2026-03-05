package com.mynotionai.dto;

import com.mynotionai.entity.ChatLog;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatLogResponse {
    private Long id;
    private ChatLog.Role role;
    private String content;
    private LocalDateTime createdAt;
    
    public static ChatLogResponse from(ChatLog chatLog) {
        return ChatLogResponse.builder()
            .id(chatLog.getId())
            .role(chatLog.getRole())
            .content(chatLog.getContent())
            .createdAt(chatLog.getCreatedAt())
            .build();
    }
}
