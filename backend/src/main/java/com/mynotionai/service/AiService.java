package com.mynotionai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mynotionai.dto.AiAnalyzeResponse;
import com.mynotionai.dto.AiScheduleDraft;
import com.mynotionai.entity.AiAction;
import com.mynotionai.entity.ChatLog;
import com.mynotionai.entity.User;
import com.mynotionai.repository.AiActionRepository;
import com.mynotionai.repository.ChatLogRepository;
import com.mynotionai.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AiService {

    private static final Pattern TIME_PATTERN = Pattern.compile("\\b([01]?\\d|2[0-3])(?::([0-5]\\d))?\\b");
    private final UserRepository userRepository;
    private final ChatLogRepository chatLogRepository;
    private final AiActionRepository aiActionRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public AiAnalyzeResponse analyzeScheduleText(Long userId, String content) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        saveChat(user, ChatLog.Role.USER, content);

        LocalDate date = resolveDate(content);
        Integer hour = resolveHour(content);
        Integer minute = resolveMinute(content);

        if (hour == null) {
            AiAnalyzeResponse response = AiAnalyzeResponse.builder()
                .resultType(AiAnalyzeResponse.ResultType.NEEDS_CLARIFICATION)
                .message("Time is unclear. What time should I schedule it?")
                .draft(null)
                .build();
            saveChat(user, ChatLog.Role.ASSISTANT, response.getMessage());
            saveAiAction(user, AiAction.Status.FAILED, response.getMessage(), null);
            return response;
        }

        LocalDateTime startAt = date.atTime(hour, minute == null ? 0 : minute);
        LocalDateTime endAt = startAt.plusHours(1);
        AiScheduleDraft draft = AiScheduleDraft.builder()
            .title(buildTitle(content))
            .startAt(startAt)
            .endAt(endAt)
            .memo(content)
            .build();

        AiAnalyzeResponse response = AiAnalyzeResponse.builder()
            .resultType(AiAnalyzeResponse.ResultType.PROPOSAL)
            .message("Draft created. Please apply, edit, or cancel.")
            .draft(draft)
            .build();

        saveChat(user, ChatLog.Role.ASSISTANT, response.getMessage());
        saveAiAction(user, AiAction.Status.SUCCESS, null, draft);
        return response;
    }

    private LocalDate resolveDate(String content) {
        LocalDate today = LocalDate.now();
        String normalized = content.toLowerCase();
        if (normalized.contains("tomorrow") || content.contains("내일")) {
            return today.plusDays(1);
        }
        if (content.contains("모레")) {
            return today.plusDays(2);
        }
        return today;
    }

    private Integer resolveHour(String content) {
        Matcher matcher = TIME_PATTERN.matcher(content);
        if (!matcher.find()) {
            return null;
        }
        return Integer.parseInt(matcher.group(1));
    }

    private Integer resolveMinute(String content) {
        Matcher matcher = TIME_PATTERN.matcher(content);
        if (!matcher.find()) {
            return null;
        }
        String minute = matcher.group(2);
        return minute == null ? 0 : Integer.parseInt(minute);
    }

    private String buildTitle(String content) {
        String normalized = content
            .replace("오늘", "")
            .replace("내일", "")
            .replace("모레", "")
            .trim();
        if (normalized.length() > 50) {
            return normalized.substring(0, 50);
        }
        return normalized.isBlank() ? "New schedule" : normalized;
    }

    private void saveChat(User user, ChatLog.Role role, String message) {
        ChatLog chatLog = ChatLog.builder()
            .user(user)
            .role(role)
            .content(message)
            .build();
        chatLogRepository.save(chatLog);
    }

    private void saveAiAction(User user, AiAction.Status status, String failReason, AiScheduleDraft draft) {
        String payload = "{}";
        if (draft != null) {
            try {
                payload = objectMapper.writeValueAsString(draft);
            } catch (JsonProcessingException ignored) {
                payload = "{\"error\":\"serialize_failed\"}";
            }
        }

        AiAction action = AiAction.builder()
            .user(user)
            .actionType(AiAction.ActionType.CREATE_SCHEDULE)
            .targetSchedule(null)
            .payloadJson(payload)
            .status(status)
            .failReason(failReason)
            .build();
        aiActionRepository.save(action);
    }
}
