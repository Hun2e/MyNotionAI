package com.mynotionai.controller;

import com.mynotionai.dto.AiAnalyzeRequest;
import com.mynotionai.dto.AiAnalyzeResponse;
import com.mynotionai.service.AiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
