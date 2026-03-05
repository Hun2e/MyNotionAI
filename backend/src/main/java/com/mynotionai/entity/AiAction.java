package com.mynotionai.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ai_actions", indexes = {
    @Index(name = "idx_user_created", columnList = "user_id, created_at"),
    @Index(name = "idx_schedule", columnList = "target_schedule_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiAction {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ActionType actionType;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_schedule_id", nullable = true)
    private Schedule targetSchedule;
    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payloadJson;
    
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Status status;
    
    @Column(nullable = true)
    private String failReason;
    
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
    
    public enum ActionType {
        CREATE_SCHEDULE, UPDATE_SCHEDULE, DELETE_SCHEDULE
    }
    
    public enum Status {
        SUCCESS, FAILED
    }
}
