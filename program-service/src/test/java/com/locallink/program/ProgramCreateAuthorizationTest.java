package com.locallink.program;

import com.locallink.program.config.SecurityConfig;
import com.locallink.program.controller.ProgramController;
import com.locallink.program.dto.ProgramDto;
import com.locallink.program.entity.Program;
import com.locallink.program.service.ForbiddenException;
import com.locallink.program.service.ProgramService;
import com.locallink.program.service.UserServiceClient;
import com.locallink.program.service.UserServiceUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** POST /api/programs 는 지자체 담당자(GOV_OFFICER)만 가능하다 - 서비스 레벨에서 역할을 검증한다. */
@WebMvcTest(ProgramController.class)
@Import(SecurityConfig.class)
class ProgramCreateAuthorizationTest {

    private static final String BODY =
            "{\"title\":\"당일 공동배송\",\"description\":\"설명\",\"category\":\"SAME_DAY\",\"price\":99000}";

    @Autowired MockMvc mockMvc;
    @MockitoBean ProgramService programService;
    @MockitoBean UserServiceClient userServiceClient;

    @Test
    void 지자체_담당자는_등록할_수_있다() throws Exception {
        when(programService.createProgram(any(), any())).thenReturn(ProgramDto.ProgramResponse.builder()
                .id(1L).title("당일 공동배송").category(Program.Category.SAME_DAY)
                .price(BigDecimal.valueOf(99000)).officerId(7L).status(Program.Status.ACTIVE).build());

        mockMvc.perform(post("/api/programs").header("X-User-Id", 7)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.officerId").value(7));

        verify(userServiceClient).requireOfficer(7L);
    }

    @Test
    void 담당자가_아니면_403이고_등록되지_않는다() throws Exception {
        doThrow(new ForbiddenException("지자체 담당자만 프로그램을 등록할 수 있습니다"))
                .when(userServiceClient).requireOfficer(5L);

        mockMvc.perform(post("/api/programs").header("X-User-Id", 5)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("지자체 담당자만 프로그램을 등록할 수 있습니다"));

        verifyNoInteractions(programService);
    }

    @Test
    void 역할을_확인하지_못하면_503이고_등록되지_않는다() throws Exception {
        doThrow(new UserServiceUnavailableException("권한을 확인하지 못했습니다. 잠시 후 다시 시도해 주세요", null))
                .when(userServiceClient).requireOfficer(7L);

        mockMvc.perform(post("/api/programs").header("X-User-Id", 7)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isServiceUnavailable());

        verifyNoInteractions(programService);
    }

    @Test
    void X_User_Id_헤더가_없으면_401이다() throws Exception {
        mockMvc.perform(post("/api/programs")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userServiceClient, programService);
    }
}
