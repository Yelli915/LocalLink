package com.locallink.program.controller;

import com.locallink.program.dto.ProgramDto;
import com.locallink.program.entity.Program;
import com.locallink.program.service.ProgramService;
import com.locallink.program.service.UserServiceClient;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/programs")
@RequiredArgsConstructor
public class ProgramController {

    private final ProgramService programService;
    private final UserServiceClient userServiceClient;

    /**
     * POST /programs - 프로그램 등록 (지자체 담당자만)
     * Gateway에서 전달한 X-User-Id 헤더로 지자체 담당자 ID 추출
     */
    @PostMapping
    public ResponseEntity<ProgramDto.ApiResponse<ProgramDto.ProgramResponse>> createProgram(
            @Valid @RequestBody ProgramDto.CreateRequest request,
            @RequestHeader("X-User-Id") Long officerId) {

        // 역할 확인은 트랜잭션 밖(컨트롤러)에서: 지자체 담당자(GOV_OFFICER)가 아니면 403, 확인 불가면 503
        userServiceClient.requireOfficer(officerId);

        ProgramDto.ProgramResponse response = programService.createProgram(request, officerId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ProgramDto.ApiResponse.success(response));
    }

    /**
     * GET /programs - 전체 프로그램 목록
     */
    @GetMapping
    public ResponseEntity<ProgramDto.ApiResponse<List<ProgramDto.ProgramResponse>>> getAllPrograms() {
        return ResponseEntity.ok(
                ProgramDto.ApiResponse.success(programService.getAllPrograms())
        );
    }

    /**
     * GET /programs/{id} - 프로그램 상세
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProgramDto.ApiResponse<ProgramDto.ProgramResponse>> getProgram(
            @PathVariable Long id) {
        return ResponseEntity.ok(
                ProgramDto.ApiResponse.success(programService.getProgram(id))
        );
    }

    /**
     * GET /programs/category/{category} - 카테고리별 프로그램
     */
    @GetMapping("/category/{category}")
    public ResponseEntity<ProgramDto.ApiResponse<List<ProgramDto.ProgramResponse>>> getProgramsByCategory(
            @PathVariable Program.Category category) {
        return ResponseEntity.ok(
                ProgramDto.ApiResponse.success(programService.getProgramsByCategory(category))
        );
    }

    /**
     * GET /programs/internal/exists/{id} - 프로그램 존재 여부 (Participation Service 호출)
     */
    @GetMapping("/internal/exists/{id}")
    public ResponseEntity<Boolean> existsProgram(@PathVariable Long id) {
        return ResponseEntity.ok(programService.existsProgram(id));
    }

    /**
     * GET /programs/internal/{id} - 프로그램 상세 조회 (Participation Service 내부 호출용)
     * - 내 참여 목록 응답 조립 시 사용
     * - 래퍼 없이 ProgramResponse만 직접 반환
     */
    @GetMapping("/internal/{id}")
    public ResponseEntity<ProgramDto.ProgramResponse> getProgramInternal(@PathVariable Long id) {
        return ResponseEntity.ok(programService.getProgram(id));
    }

    /**
     * POST /programs/internal/{id}/participation-count - 참여자 수 증가 (Participation Service 호출)
     */
    @PostMapping("/internal/{id}/participation-count")
    public ResponseEntity<Void> increaseParticipationCount(@PathVariable Long id) {
        programService.increaseParticipationCount(id);
        return ResponseEntity.ok().build();
    }

    /**
     * GET /programs/internal/recommend - 추천 서비스용 미참여 프로그램 조회
     * category: 카테고리, excludeIds: 이미 참여한 프로그램 ID 목록
     */
    @GetMapping("/internal/recommend")
    public ResponseEntity<List<ProgramDto.ProgramResponse>> getRecommendPrograms(
            @RequestParam Program.Category category,
            @RequestParam(defaultValue = "") List<Long> excludeIds) {
        return ResponseEntity.ok(programService.getRecommendPrograms(category, excludeIds));
    }
}