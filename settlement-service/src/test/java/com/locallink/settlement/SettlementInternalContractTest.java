package com.locallink.settlement;

import com.locallink.settlement.config.SecurityConfig;
import com.locallink.settlement.controller.SettlementController;
import com.locallink.settlement.dto.SettlementDto;
import com.locallink.settlement.service.SettlementService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * participation-service 가 의존하는 POST /api/settlements/internal/request 계약을 고정한다.
 *  요청: { userId, programId, amount }   응답: { settlementId, status: COMPLETED | FAILED }
 * 응답은 항상 래퍼 없이 이 두 필드만 온다 (participation-service 의 SettlementResult 가 그대로 역직렬화).
 */
@WebMvcTest(SettlementController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "internal.api-token=test-token")
class SettlementInternalContractTest {

    private static final String TOKEN_HEADER = "X-Internal-Token";
    private static final String TOKEN = "test-token";

    @Autowired MockMvc mockMvc;
    @MockitoBean SettlementService settlementService;

    private static final String REQUEST = "{\"userId\":1,\"programId\":10,\"amount\":99000}";

    @Test
    void 요청_필드를_서비스로_전달하고_COMPLETED_결과를_반환한다() throws Exception {
        when(settlementService.processInternalSettlement(any())).thenReturn(
                SettlementDto.InternalSettlementResult.builder().settlementId(5L).status("COMPLETED").build());

        mockMvc.perform(post("/api/settlements/internal/request")
                        .header(TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settlementId").value(5))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data").doesNotExist());

        ArgumentCaptor<SettlementDto.InternalSettlementRequest> captor =
                ArgumentCaptor.forClass(SettlementDto.InternalSettlementRequest.class);
        verify(settlementService).processInternalSettlement(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(1L);
        assertThat(captor.getValue().getProgramId()).isEqualTo(10L);
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo(BigDecimal.valueOf(99000));
    }

    @Test
    void 정산_실패도_200과_FAILED_상태로_동기_응답한다() throws Exception {
        when(settlementService.processInternalSettlement(any())).thenReturn(
                SettlementDto.InternalSettlementResult.builder().settlementId(6L).status("FAILED").build());

        mockMvc.perform(post("/api/settlements/internal/request")
                        .header(TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));
    }

    @Test
    void 토큰이_없거나_틀리면_정산_요청은_403이고_서비스는_호출되지_않는다() throws Exception {
        mockMvc.perform(post("/api/settlements/internal/request")
                        .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/settlements/internal/request")
                        .header(TOKEN_HEADER, "wrong")
                        .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isForbidden());

        verifyNoInteractions(settlementService);
    }
}
