package com.mynotionai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mynotionai.dto.AiAnalyzeResponse;
import com.mynotionai.dto.AiApplyResponse;
import com.mynotionai.dto.AiScheduleDraft;
import com.mynotionai.dto.AiSimpleResponse;
import com.mynotionai.dto.AiTodaySummaryResponse;
import com.mynotionai.dto.ChatLogResponse;
import com.mynotionai.dto.ScheduleResponse;
import com.mynotionai.entity.AiAction;
import com.mynotionai.entity.ChatLog;
import com.mynotionai.entity.Schedule;
import com.mynotionai.entity.User;
import com.mynotionai.repository.AiActionRepository;
import com.mynotionai.repository.ChatLogRepository;
import com.mynotionai.repository.ScheduleRepository;
import com.mynotionai.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AiService {

    private static final Pattern TIME_PATTERN = Pattern.compile("\\b([01]?\\d|2[0-3])(?::([0-5]\\d))?\\b");
    private final UserRepository userRepository;
    private final ScheduleRepository scheduleRepository;
    private final ChatLogRepository chatLogRepository;
    private final AiActionRepository aiActionRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public AiAnalyzeResponse analyzeScheduleText(Long userId, String content) {
        User user = getUser(userId);
        saveChat(user, ChatLog.Role.USER, content);

        LocalDate date = resolveDate(content);
        Integer hour = resolveHour(content);
        Integer minute = resolveMinute(content);

        if (hour == null) {
            AiAnalyzeResponse response = AiAnalyzeResponse.builder()
                .resultType(AiAnalyzeResponse.ResultType.NEEDS_CLARIFICATION)
                .message("시간이 불명확해요. 몇 시로 잡을까요?")
                .draft(null)
                .build();

            saveChat(user, ChatLog.Role.ASSISTANT, response.getMessage());
            saveAiAction(user, AiAction.ActionType.CREATE_SCHEDULE, null, response, AiAction.Status.FAILED, "TIME_UNCLEAR");
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
            .message("일정 초안을 만들었어요. 적용/수정/취소를 선택해주세요.")
            .draft(draft)
            .build();

        saveChat(user, ChatLog.Role.ASSISTANT, response.getMessage());
        saveAiAction(user, AiAction.ActionType.CREATE_SCHEDULE, null, response, AiAction.Status.SUCCESS, null);
        return response;
    }

    @Transactional
    public AiApplyResponse applyDraft(Long userId, AiScheduleDraft draft) {
        User user = getUser(userId);
        validateDraft(draft);

        Schedule schedule = Schedule.builder()
            .user(user)
            .title(draft.getTitle())
            .startAt(draft.getStartAt())
            .endAt(draft.getEndAt())
            .memo(draft.getMemo())
            .createdBy(Schedule.CreatedBy.AI)
            .build();
        Schedule saved = scheduleRepository.save(schedule);

        saveChat(user, ChatLog.Role.ASSISTANT, "일정을 캘린더에 적용했어요.");
        saveAiAction(user, AiAction.ActionType.CREATE_SCHEDULE, saved, draft, AiAction.Status.SUCCESS, null);

        return AiApplyResponse.builder()
            .message("일정이 적용되었어요.")
            .schedule(ScheduleResponse.from(saved))
            .build();
    }

    @Transactional
    public AiSimpleResponse cancelDraft(Long userId, AiScheduleDraft draft, String reason) {
        User user = getUser(userId);
        saveChat(user, ChatLog.Role.USER, "Canceled AI draft: " + reason);
        saveAiAction(user, AiAction.ActionType.CREATE_SCHEDULE, null, draft, AiAction.Status.FAILED, "CANCELED_BY_USER: " + reason);
        return AiSimpleResponse.builder()
            .message("초안을 취소했어요.")
            .build();
    }

    @Transactional
    public AiAnalyzeResponse reviseDraft(Long userId, String content) {
        return analyzeScheduleText(userId, content);
    }

    @Transactional(readOnly = true)
    public List<ChatLogResponse> getChatLogs(Long userId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return chatLogRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
            .limit(safeLimit)
            .map(ChatLogResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public AiTodaySummaryResponse getTodaySummary(Long userId) {
        LocalDate today = LocalDate.now();
        LocalDateTime from = today.atStartOfDay();
        LocalDateTime to = today.plusDays(1).atStartOfDay();

        List<ScheduleResponse> schedules = scheduleRepository
            .findByUserIdAndStartAtBetweenOrderByStartAtAsc(userId, from, to)
            .stream()
            .map(ScheduleResponse::from)
            .toList();

        String summary;
        if (schedules.isEmpty()) {
            summary = "오늘 일정이 없습니다.";
        } else if (schedules.size() == 1) {
            summary = "오늘 일정 1개가 있습니다: " + schedules.get(0).getTitle();
        } else {
            summary = "오늘 일정 " + schedules.size() + "개가 있습니다. 첫 일정은 " + schedules.get(0).getTitle() + "입니다.";
        }

        return AiTodaySummaryResponse.builder()
            .summary(summary)
            .totalCount(schedules.size())
            .schedules(schedules)
            .build();
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
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

    private void validateDraft(AiScheduleDraft draft) {
        if (draft == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "draft is required");
        }
        if (draft.getTitle() == null || draft.getTitle().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title is required");
        }
        if (draft.getStartAt() == null || draft.getEndAt() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startAt and endAt are required");
        }
        if (!draft.getStartAt().isBefore(draft.getEndAt())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startAt must be before endAt");
        }
    }

    private void saveChat(User user, ChatLog.Role role, String message) {
        ChatLog chatLog = ChatLog.builder()
            .user(user)
            .role(role)
            .content(message)
            .build();
        chatLogRepository.save(chatLog);
    }

    private void saveAiAction(
        User user,
        AiAction.ActionType actionType,
        Schedule targetSchedule,
        Object payloadObject,
        AiAction.Status status,
        String failReason
    ) {
        String payloadJson = "{}";
        if (payloadObject != null) {
            try {
                payloadJson = objectMapper.writeValueAsString(payloadObject);
            } catch (JsonProcessingException ignored) {
                payloadJson = "{\"error\":\"serialize_failed\"}";
            }
        }

        AiAction action = AiAction.builder()
            .user(user)
            .actionType(actionType)
            .targetSchedule(targetSchedule)
            .payloadJson(payloadJson)
            .status(status)
            .failReason(failReason)
            .build();
        aiActionRepository.save(action);
    }
}
