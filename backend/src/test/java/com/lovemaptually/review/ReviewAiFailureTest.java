package com.lovemaptually.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lovemaptually.auth.dto.request.SignupRequest;
import com.lovemaptually.place.dto.request.AddGroupPlaceRequest;
import com.lovemaptually.place.dto.request.PlaceInput;
import com.lovemaptually.review.dto.request.CreateReviewRequest;
import com.lovemaptually.support.DatabaseCleanup;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * AI가 죽어도 리뷰는 남습니다. AiClientSelector가 app.ai.client 값으로 구현을 고르므로
 * failing 구현을 물려 놓고 같은 엔드포인트를 그대로 부릅니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.ai.client=failing")
@Testcontainers(disabledWithoutDocker = true)
class ReviewAiFailureTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.jwt.secret", () -> "ai-failure-test-secret-key-longer-than-32-bytes");
        registry.add("app.jwt.issuer", () -> "lovemaptually-test");
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDomainTables() {
        DatabaseCleanup.clean(jdbcTemplate);
    }

    @Test
    void tagExtractionFailureStillSavesTheReviewAndIsReportedByTagStatus() throws Exception {
        String token = signup("aifail@example.com", "AI실패");
        long groupId = createCoupleGroup(token, "우리 둘");
        long placeId = addPlace(token, groupId, "kakao-1", "○○찻집", "인사동");

        CreateReviewRequest request = new CreateReviewRequest(placeId, groupId,
                LocalDate.parse("2026-09-01"), 4, "웨이팅이 40분이라 힘들었는데 안 매워서 좋았어요");

        MvcResult result = mockMvc.perform(post("/api/reviews")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.data.tagStatus").value("FAILED"))
                .andReturn();

        JsonNode data = dataOf(result);
        assertThat(data.path("tags").size()).isZero();

        long reviewId = data.path("reviewId").asLong();
        Integer savedRows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM reviews WHERE review_id = ? AND tag_status = 'FAILED'",
                Integer.class, reviewId);
        Integer tagRows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM review_tags WHERE review_id = ?", Integer.class, reviewId);

        assertThat(savedRows).isEqualTo(1);
        assertThat(tagRows).isZero();
    }

    private long addPlace(String token, long groupId, String providerPlaceId, String name, String region)
            throws Exception {
        AddGroupPlaceRequest request = new AddGroupPlaceRequest(null, new PlaceInput(
                "kakao", providerPlaceId, name, "서울시 종로구 " + name, region, "카페", 2,
                new BigDecimal("37.5740000"), new BigDecimal("126.9850000")));
        return dataOf(mockMvc.perform(post("/api/groups/" + groupId + "/places")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()).path("placeId").asLong();
    }

    private long createCoupleGroup(String token, String name) throws Exception {
        return dataOf(mockMvc.perform(post("/api/groups")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"groupType\":\"COUPLE\",\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn()).path("groupId").asLong();
    }

    private JsonNode dataOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private String signup(String email, String nickname) throws Exception {
        String body = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SignupRequest(email, "password123!", nickname))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("accessToken").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
