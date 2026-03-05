package com.mynotionai.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "schedules", indexes = {
    @Index(name = "idx_user_start", columnList = "user_id, start_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Schedule {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(nullable = false)
    private String title;
    
    @Column(nullable = false)
    private LocalDateTime startAt;
    
    @Column(nullable = false)
    private LocalDateTime endAt;
    
    @Column(columnDefinition = "TEXT")
    private String memo;
    
    @Column(nullable = false, columnDefinition = "VARCHAR(20) DEFAULT 'USER'")
    @Enumerated(EnumType.STRING)
    private CreatedBy createdBy;
    
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.createdBy == null) {
            this.createdBy = CreatedBy.USER;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
    
    public enum CreatedBy {
        USER, AI
    }
}
