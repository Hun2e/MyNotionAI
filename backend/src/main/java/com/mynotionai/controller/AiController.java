package com.mynotionai.controller;

import com.mynotionai.dto.AiAnalyzeRequest;
import com.mynotionai.dto.AiAnalyzeResponse;
import com.mynotionai.dto.AiApplyRequest;
import com.mynotionai.dto.AiApplyResponse;
import com.mynotionai.dto.AiCancelRequest;
import com.mynotionai.dto.AiSimpleResponse;
import com.mynotionai.dto.AiTodaySummaryResponse;
import com.mynotionai.dto.ChatLogResponse;
import com.mynotionai.service.AiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;

    @PostMapping("/analyze")
    public ResponseEntity<AiAnalyzeResponse> analyze(
        Authentication authentication,
        @Valid @RequestBody AiAnalyzeRequest request
    ) {
        Long userId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(aiService.analyzeScheduleText(userId, request.getContent()));
    }

    @PostMapping("/apply")
    public ResponseEntity<AiApplyResponse> apply(
        Authentication authentication,
        @Valid @RequestBody AiApplyRequest request
    ) {
        Long userId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(aiService.applyDraft(userId, request.getDraft()));
    }

    @PostMapping("/revise")
    public ResponseEntity<AiAnalyzeResponse> revise(
        Authentication authentication,
        @Valid @RequestBody AiAnalyzeRequest request
    ) {
        Long userId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(aiService.reviseDraft(userId, request.getContent()));
    }

    @PostMapping("/cancel")
    public ResponseEntity<AiSimpleResponse> cancel(
        Authentication authentication,
        @Valid @RequestBody AiCancelRequest request
    ) {
        Long userId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(aiService.cancelDraft(userId, request.getDraft(), request.getReason()));
    }

    @GetMapping("/chat-logs")
    public ResponseEntity<List<ChatLogResponse>> chatLogs(
        Authentication authentication,
        @RequestParam(defaultValue = "30") int limit
    ) {
        Long userId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(aiService.getChatLogs(userId, limit));
    }

    @GetMapping("/today-summary")
    public ResponseEntity<AiTodaySummaryResponse> todaySummary(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(aiService.getTodaySummary(userId));
    }
}
