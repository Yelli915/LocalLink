package com.locallink.program;

import com.locallink.program.config.SecurityConfig;
import com.locallink.program.controller.ProgramController;
import com.locallink.program.dto.ProgramDto;
import com.locallink.program.entity.Program;
import com.locallink.program.service.ProgramService;
import com.locallink.program.service.UserServiceClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 다른 서비스가 의존하는 program-service 계약(요청 파라미터·응답 필드)을 고정한다.
 *  - participation-service : internal/exists, internal/{id}, internal/{id}/participation-count
 *  - recommend-service  : internal/recommend, 그리고 GET /api/programs 의 {data:[...]} 래퍼
 * 필드명/래퍼 여부가 바뀌면 소비자 쪽이 조용히 깨지므로, 여기서 먼저 실패하게 한다.
 */
@WebMvcTest(ProgramController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "internal.api-token=test-token")
class ProgramInternalContractTest {

    private static final String TOKEN_HEADER = "X-Internal-Token";
    private static final String TOKEN = "test-token";

    @Autowired MockMvc mockMvc;
    @MockitoBean ProgramService programService;
    @MockitoBean UserServiceClient userServiceClient;

    private ProgramDto.ProgramResponse program(long id, Program.Category category, int participationCount) {
        return ProgramDto.ProgramResponse.builder()
                .id(id).title("프로그램 " + id).description("설명")
                .category(category).price(BigDecimal.valueOf(99000))
                .officerId(7L).participationCount(participationCount)
                .status(Program.Status.ACTIVE)
                .build();
    }

    @Test
    void exists는_래퍼_없이_boolean만_반환한다() throws Exception {
        when(programService.existsProgram(1L)).thenReturn(true);
        when(programService.existsProgram(2L)).thenReturn(false);

        mockMvc.perform(get("/api/programs/internal/exists/1").header(TOKEN_HEADER, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(true));
        mockMvc.perform(get("/api/programs/internal/exists/2").header(TOKEN_HEADER, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(false));
    }

    @Test
    void internal_상세는_래퍼_없이_프로그램_객체를_직접_반환한다() throws Exception {
        when(programService.getProgram(1L)).thenReturn(program(1L, Program.Category.SAME_DAY, 3));

        mockMvc.perform(get("/api/programs/internal/1").header(TOKEN_HEADER, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("프로그램 1"))
                .andExpect(jsonPath("$.category").value("SAME_DAY"))
                .andExpect(jsonPath("$.price").value(99000))
                .andExpect(jsonPath("$.participationCount").value(3))
                .andExpect(jsonPath("$.officerId").value(7))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void 존재하지_않는_프로그램의_internal_상세는_400과_메시지를_준다() throws Exception {
        when(programService.getProgram(99L)).thenThrow(new IllegalArgumentException("프로그램을 찾을 수 없습니다: 99"));

        mockMvc.perform(get("/api/programs/internal/99").header(TOKEN_HEADER, TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("프로그램을 찾을 수 없습니다: 99"));
    }

    @Test
    void participation_count_증가는_200을_주고_서비스를_호출한다() throws Exception {
        mockMvc.perform(post("/api/programs/internal/5/participation-count").header(TOKEN_HEADER, TOKEN))
                .andExpect(status().isOk());

        verify(programService).increaseParticipationCount(5L);
    }

    @Test
    void recommend는_category와_excludeIds를_받아_프로그램_배열을_반환한다() throws Exception {
        when(programService.getRecommendPrograms(Program.Category.SAME_DAY, List.of(1L, 2L)))
                .thenReturn(List.of(program(3L, Program.Category.SAME_DAY, 10)));

        mockMvc.perform(get("/api/programs/internal/recommend").header(TOKEN_HEADER, TOKEN)
                        .param("category", "SAME_DAY")
                        .param("excludeIds", "1,2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(3))
                .andExpect(jsonPath("$[0].category").value("SAME_DAY"))
                .andExpect(jsonPath("$[0].participationCount").value(10));
    }

    @Test
    void recommend의_excludeIds는_생략할_수_있다() throws Exception {
        when(programService.getRecommendPrograms(Program.Category.COLD_CHAIN, List.of()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/programs/internal/recommend").header(TOKEN_HEADER, TOKEN).param("category", "COLD_CHAIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void 전체_목록은_success_data_래퍼를_유지한다() throws Exception {
        when(programService.getAllPrograms()).thenReturn(List.of(program(1L, Program.Category.SHARED_STORAGE, 0)));

        mockMvc.perform(get("/api/programs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].category").value("SHARED_STORAGE"));
    }

    @Test
    void 토큰이_없거나_틀리면_internal_API는_403이다() throws Exception {
        mockMvc.perform(get("/api/programs/internal/exists/1"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/programs/internal/1").header(TOKEN_HEADER, "wrong"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/programs/internal/5/participation-count"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(programService);
    }

    @Test
    void 공개_조회_경로는_토큰_없이_접근할_수_있다() throws Exception {
        when(programService.getAllPrograms()).thenReturn(List.of());

        mockMvc.perform(get("/api/programs"))
                .andExpect(status().isOk());
    }
}
