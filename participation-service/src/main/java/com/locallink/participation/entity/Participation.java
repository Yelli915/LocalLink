package com.locallink.participation.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "participations",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "program_id"}))
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class Participation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "program_id", nullable = false)
    private Long programId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Status status = Status.PENDING;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    public enum Status {
        PENDING,   // 참여신청 완료, 정산 대기
        ACTIVE,    // 정산 완료, 참여 활성화
        CANCELLED, // 취소
        FAILED     // 정산 요청 실패 (보상 처리됨) - 재신청 가능
    }

    // 상태 전이(PENDING→ACTIVE/FAILED, FAILED→PENDING)는 동시성 안전을 위해
    // ParticipationRepository 의 조건부 UPDATE 로만 수행한다 (엔티티 setter 로 바꾸지 않는다).

    public void cancel() {
        this.status = Status.CANCELLED;
    }
}
