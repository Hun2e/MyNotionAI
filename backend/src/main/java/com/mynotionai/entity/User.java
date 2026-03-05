package com.mynotionai.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users", uniqueConstraints = {
    @UniqueConstraint(columnNames = "email"),
    @UniqueConstraint(columnNames = {"provider", "provider_user_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String email;
    
    @Column(nullable = true)
    private String passwordHash;
    
    @Column(nullable = false)
    private String nickname;
    
    @Column(nullable = false, columnDefinition = "VARCHAR(20) DEFAULT 'LOCAL'")
    @Enumerated(EnumType.STRING)
    private Provider provider;
    
    @Column(name = "provider_user_id", nullable = true)
    private String providerUserId;
    
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.provider == null) {
            this.provider = Provider.LOCAL;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
    
    public enum Provider {
        LOCAL, GOOGLE
    }
}
