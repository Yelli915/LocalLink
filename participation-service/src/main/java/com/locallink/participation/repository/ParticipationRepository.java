package com.locallink.participation.repository;

import com.locallink.participation.entity.Participation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ParticipationRepository extends JpaRepository<Participation, Long> {

    List<Participation> findByUserId(Long userId);

    List<Participation> findByUserIdAndStatus(Long userId, Participation.Status status);

    Optional<Participation> findByUserIdAndProgramId(Long userId, Long programId);

    boolean existsByUserIdAndProgramId(Long userId, Long programId);

    // 중복 신청 판단용 - FAILED 는 재신청 가능하므로 제외
    boolean existsByUserIdAndProgramIdAndStatusIn(Long userId, Long programId, List<Participation.Status> statuses);

    // ---- 상태 전이: 모두 "현재 상태 조건부 UPDATE" 한 문장으로 수행한다 ----
    // 엔티티를 읽고(read) 고쳐 쓰면(write) 그 사이에 다른 트랜잭션(정산 이벤트 / 다른 인스턴스의 정리 작업)이
    // 바꾼 상태를 덮어쓸 수 있다(lost update). 조건부 UPDATE 는 DB 가 원자적으로 판정하므로
    // 반환값(변경 행 수)이 곧 "내가 전이시켰는가" 이다.

    /** PENDING 일 때만 FAILED 로 (이미 ACTIVE 가 된 건 덮어쓰지 않는다) */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Participation e set e.status = :to, e.updatedAt = :now "
            + "where e.id = :id and e.status = :from")
    int transition(@Param("id") Long id,
                   @Param("from") Participation.Status from,
                   @Param("to") Participation.Status to,
                   @Param("now") java.time.LocalDateTime now);

    /** 여러 상태 중 하나일 때 전이 (예: PENDING/FAILED → ACTIVE, FAILED/CANCELLED → PENDING) */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Participation e set e.status = :to, e.updatedAt = :now "
            + "where e.id = :id and e.status in :from")
    int transitionFromAny(@Param("id") Long id,
                          @Param("from") List<Participation.Status> from,
                          @Param("to") Participation.Status to,
                          @Param("now") java.time.LocalDateTime now);

    /** 오래된 PENDING 일괄 FAILED (다중 인스턴스에서 동시에 실행돼도 안전) */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Participation e set e.status = :to, e.updatedAt = :now "
            + "where e.status = :from and coalesce(e.updatedAt, e.createdAt) < :threshold")
    int transitionStale(@Param("from") Participation.Status from,
                        @Param("to") Participation.Status to,
                        @Param("threshold") java.time.LocalDateTime threshold,
                        @Param("now") java.time.LocalDateTime now);

    // 참여 완료(ACTIVE)된 프로그램 ID 목록 - 추천 서비스용
    List<Participation> findByUserIdAndStatusIn(Long userId, List<Participation.Status> statuses);
}
