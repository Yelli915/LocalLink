package com.locallink.participation.dto;

import com.locallink.participation.entity.Participation;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

public class ParticipationDto {

    // 참여신청 요청
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ParticipateRequest {
        @NotNull(message = "프로그램 ID는 필수입니다")
        private Long programId;
    }

    // 프로그램 요약 정보 (내 참여 목록 표시용)
    // program-service 응답을 그대로 매핑한다: 모르는 필드는 무시하고, 필드명 변형은 alias 로 흡수한다.
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ProgramSummary {
        private Long id;
        private String title;
        private String description;
        private String category;
        private Integer price;
        private String thumbnail;
        @JsonAlias("officer_name")
        private String officerName;
        @JsonAlias("participation_count")
        private Integer participationCount;
    }

    // 참여 응답
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ParticipationResponse {
        private Long id;
        private Long userId;
        private Long programId;
        private Participation.Status status;
        private LocalDateTime createdAt;

        // 추가
        private ProgramSummary program;

        public static ParticipationResponse from(Participation participation) {
            return ParticipationResponse.builder()
                    .id(participation.getId())
                    .userId(participation.getUserId())
                    .programId(participation.getProgramId())
                    .status(participation.getStatus())
                    .createdAt(participation.getCreatedAt())
                    .build();
        }

        public static ParticipationResponse from(Participation participation, ProgramSummary program) {
            return ParticipationResponse.builder()
                    .id(participation.getId())
                    .userId(participation.getUserId())
                    .programId(participation.getProgramId())
                    .status(participation.getStatus())
                    .createdAt(participation.getCreatedAt())
                    .program(program)
                    .build();
        }
    }

    // 추천 서비스용: 참여 이력 조회 응답
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ParticipationHistoryResponse {
        private Long userId;
        private List<Long> activeProgramIds;
    }

    // 공통 API 응답 래퍼
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ApiResponse<T> {
        private boolean success;
        private String message;
        private T data;

        public static <T> ApiResponse<T> success(T data) {
            return ApiResponse.<T>builder()
                    .success(true)
                    .message("성공")
                    .data(data)
                    .build();
        }

        public static <T> ApiResponse<T> error(String message) {
            return ApiResponse.<T>builder()
                    .success(false)
                    .message(message)
                    .build();
        }
    }
}