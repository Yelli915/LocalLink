package com.locallink.program.repository;

import com.locallink.program.entity.Program;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProgramRepository extends JpaRepository<Program, Long> {

    // 카테고리별 프로그램 조회 (추천 서비스 사용)
    List<Program> findByCategoryAndStatus(Program.Category category, Program.Status status);

    // 지자체 담당자별 프로그램 조회
    List<Program> findByOfficerId(Long officerId);

    // 활성 프로그램 전체 조회
    List<Program> findByStatus(Program.Status status);

    // 카테고리별 + 특정 ID 제외 조회 (추천 서비스: 이미 참여한 프로그램 제외)
    List<Program> findByCategoryAndStatusAndIdNotIn(
            Program.Category category,
            Program.Status status,
            List<Long> excludeIds
    );
}
