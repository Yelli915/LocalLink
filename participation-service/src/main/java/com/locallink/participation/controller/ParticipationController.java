package com.locallink.participation.controller;

import com.locallink.participation.dto.ParticipationDto;
import com.locallink.participation.service.ParticipationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/participations")
@RequiredArgsConstructor
public class ParticipationController {

    private final ParticipationService participationService;

    /**
     * POST /participations - 참여신청
     * Gateway에서 X-User-Id 헤더로 사용자 ID 전달
     */
    @PostMapping
    public ResponseEntity<ParticipationDto.ApiResponse<ParticipationDto.ParticipationResponse>> participate(
            @Valid @RequestBody ParticipationDto.ParticipateRequest request,
            @RequestHeader("X-User-Id") Long userId) {

        ParticipationDto.ParticipationResponse response =
                participationService.participate(userId, request.getProgramId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ParticipationDto.ApiResponse.success(response));
    }

    /**
     * GET /participations/my - 내 참여 목록 조회
     * Gateway가 전달한 X-User-Id 헤더를 사용
     */
    @GetMapping("/my")
    public ResponseEntity<ParticipationDto.ApiResponse<List<ParticipationDto.ParticipationResponse>>> getMyParticipations(
            @RequestHeader("X-User-Id") Long userId) {

        List<ParticipationDto.ParticipationResponse> response =
                participationService.getParticipationsByUser(userId);
        return ResponseEntity.ok(ParticipationDto.ApiResponse.success(response));
    }

    /**
     * GET /participations/user/{userId} - 특정 사용자 참여 목록 조회
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<ParticipationDto.ApiResponse<List<ParticipationDto.ParticipationResponse>>> getParticipations(
            @PathVariable Long userId) {

        List<ParticipationDto.ParticipationResponse> response =
                participationService.getParticipationsByUser(userId);
        return ResponseEntity.ok(ParticipationDto.ApiResponse.success(response));
    }

    /**
     * GET /participations/internal/history/{userId} - 참여 이력 조회 (Recommend Service용)
     */
    @GetMapping("/internal/history/{userId}")
    public ResponseEntity<ParticipationDto.ParticipationHistoryResponse> getParticipationHistory(
            @PathVariable Long userId) {

        return ResponseEntity.ok(participationService.getParticipationHistory(userId));
    }
}