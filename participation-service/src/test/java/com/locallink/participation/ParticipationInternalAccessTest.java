package com.locallink.participation;

import com.locallink.participation.kafka.ParticipationKafkaProducer;
import com.locallink.participation.service.ProgramServiceClient;
import com.locallink.participation.service.SettlementServiceClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * recommend-service 가 호출하는 GET /api/participations/internal/history/{userId} 는
 * 공유 토큰(X-Internal-Token)이 있어야만 열린다. 응답 계약({ userId, activeProgramIds })도 함께 고정한다.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:internalaccess;MODE=MariaDB;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "eureka.client.enabled=false",
        "spring.kafka.listener.auto-startup=false",
        "participation.cleanup-interval-ms=3600000",
        "internal.api-token=test-token"
})
@AutoConfigureMockMvc
class ParticipationInternalAccessTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean ProgramServiceClient programServiceClient;
    @MockitoBean SettlementServiceClient settlementServiceClient;
    @MockitoBean ParticipationKafkaProducer kafkaProducer;

    @Test
    void 토큰이_없거나_틀리면_403() throws Exception {
        mockMvc.perform(get("/api/participations/internal/history/1"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/participations/internal/history/1").header("X-Internal-Token", "wrong"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 토큰이_맞으면_참여_이력_계약대로_응답한다() throws Exception {
        mockMvc.perform(get("/api/participations/internal/history/1").header("X-Internal-Token", "test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.activeProgramIds").isArray());
    }
}
