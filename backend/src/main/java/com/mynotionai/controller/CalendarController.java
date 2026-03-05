package com.mynotionai.controller;

import com.mynotionai.dto.ScheduleRequest;
import com.mynotionai.dto.ScheduleResponse;
import com.mynotionai.service.ScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/calendar")
@RequiredArgsConstructor
public class CalendarController {

    private final ScheduleService scheduleService;

    @GetMapping("/events")
    public ResponseEntity<List<ScheduleResponse>> getEvents(
        Authentication authentication,
        @RequestParam int month,
        @RequestParam int year
    ) {
        Long userId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(scheduleService.getMonthlySchedules(userId, year, month));
    }

    @PostMapping("/events")
    public ResponseEntity<ScheduleResponse> createEvent(
        Authentication authentication,
        @Valid @RequestBody ScheduleRequest request
    ) {
        Long userId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(scheduleService.createSchedule(userId, request));
    }

    @PutMapping("/events/{id}")
    public ResponseEntity<ScheduleResponse> updateEvent(
        Authentication authentication,
        @PathVariable Long id,
        @Valid @RequestBody ScheduleRequest request
    ) {
        Long userId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(scheduleService.updateSchedule(userId, id, request));
    }

    @DeleteMapping("/events/{id}")
    public ResponseEntity<Void> deleteEvent(Authentication authentication, @PathVariable Long id) {
        Long userId = (Long) authentication.getPrincipal();
        scheduleService.deleteSchedule(userId, id);
        return ResponseEntity.noContent().build();
    }
}
