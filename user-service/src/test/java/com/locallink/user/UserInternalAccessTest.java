package com.locallink.user;

import com.locallink.user.config.SecurityConfig;
import com.locallink.user.controller.UserController;
import com.locallink.user.dto.UserDto;
import com.locallink.user.entity.User;
import com.locallink.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * program-service 가 프로그램 등록 시 역할 확인에 쓰는 GET /api/users/internal/{id} 계약과 토큰 가드.
 * 응답은 래퍼 없이 { id, email, name, role, ... } 이다.
 */
@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "internal.api-token=test-token")
class UserInternalAccessTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean UserService userService;

    @Test
    void 토큰이_없거나_틀리면_internal_조회는_403이다() throws Exception {
        mockMvc.perform(get("/api/users/internal/1")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/users/internal/1").header("X-Internal-Token", "wrong"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(userService);
    }

    @Test
    void 토큰이_맞으면_role을_포함한_사용자를_래퍼_없이_반환한다() throws Exception {
        when(userService.getUserById(1L)).thenReturn(
                UserDto.UserResponse.builder().id(1L).email("a@b.c").name("담당자").role(User.Role.GOV_OFFICER).build());

        mockMvc.perform(get("/api/users/internal/1").header("X-Internal-Token", "test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.role").value("GOV_OFFICER"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}
