package com.locallink.settlement.controller;

import com.locallink.settlement.dto.SettlementDto;
import com.locallink.settlement.service.SettlementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/settlements")
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementService settlementService;

    /**
     * POST /settlements/internal/request - 내부 정산 요청 (Participation Service 호출)
     */
    @PostMapping("/internal/request")
    public ResponseEntity<SettlementDto.InternalSettlementResult> processInternalSettlement(
            @RequestBody SettlementDto.InternalSettlementRequest request) {

        SettlementDto.InternalSettlementResult result = settlementService.processInternalSettlement(request);
        return ResponseEntity.ok(result);
    }

    /**
     * GET /settlements/{id} - 정산 단건 조회
     */
    @GetMapping("/{id}")
    public ResponseEntity<SettlementDto.ApiResponse<SettlementDto.SettlementResponse>> getSettlement(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                SettlementDto.ApiResponse.success(settlementService.getSettlement(id)));
    }

    /**
     * GET /settlements/user/{userId} - 사용자 정산 내역 조회
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<SettlementDto.ApiResponse<List<SettlementDto.SettlementResponse>>> getSettlementsByUser(
            @PathVariable Long userId) {

        return ResponseEntity.ok(
                SettlementDto.ApiResponse.success(settlementService.getSettlementsByUser(userId)));
    }
}
