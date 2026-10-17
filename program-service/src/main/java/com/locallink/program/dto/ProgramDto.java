package com.locallink.program.dto;

import com.locallink.program.entity.Program;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class ProgramDto {

    // 프로그램 등록 요청
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateRequest {

        @NotBlank(message = "프로그램 제목은 필수입니다")
        private String title;

        private String description;

        @NotNull(message = "카테고리는 필수입니다")
        private Program.Category category;

        @NotNull(message = "가격은 필수입니다")
        @PositiveOrZero(message = "가격은 0 이상이어야 합니다")
        private BigDecimal price;
    }

    // 프로그램 응답
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ProgramResponse {
        private Long id;
        private String title;
        private String description;
        private Program.Category category;
        private BigDecimal price;
        private Long officerId;
        private Integer participationCount;
        private Program.Status status;
        private LocalDateTime createdAt;

        public static ProgramResponse from(Program program) {
            return ProgramResponse.builder()
                    .id(program.getId())
                    .title(program.getTitle())
                    .description(program.getDescription())
                    .category(program.getCategory())
                    .price(program.getPrice())
                    .officerId(program.getOfficerId())
                    .participationCount(program.getParticipationCount())
                    .status(program.getStatus())
                    .createdAt(program.getCreatedAt())
                    .build();
        }
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

    // 추천 서비스용 응답 (카테고리 기반 미참여 프로그램 목록)
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RecommendResponse {
        private List<ProgramResponse> programs;
        private Program.Category category;
    }
}
