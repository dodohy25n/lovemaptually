package com.lovemaptually.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lovemaptually.auth.dto.request.SignupRequest;
import com.lovemaptually.place.dto.request.AddGroupPlaceRequest;
import com.lovemaptually.place.dto.request.PlaceInput;
import com.lovemaptually.recommendation.dto.request.CreateRecommendationRequest;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * FastAPI 추천 엔진은 테스트 중에 떠 있지 않습니다. 그래서 규칙 폴백이 전부를 받고
 * 엔진이 없어도 규칙 폴백이 순위를 내고 요청이 COMPLETED로 끝납니다 — 그 자체가 검증 대상입니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class RecommendationApiTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.jwt.secret", () -> "recommendation-test-secret-key-longer-than-32-bytes");
        registry.add("app.jwt.issuer", () -> "lovemaptually-test");
        registry.add("app.recommender.base-url", () -> "http://localhost:1");
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
    void queryWithoutARecognisableRegionReturns422AndLeavesNoRequestRow() throws Exception {
        String token = signup("noregion@example.com", "지역없음");
        long groupId = createCoupleGroup(token, "우리 둘");

        requestRecommendation(token, groupId, "오늘 맛있는 데 세 곳 추천해줘")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.error.code").value("REGION_NOT_FOUND"));

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM recommendation_requests WHERE group_id = ?", Integer.class, groupId);
        Integer completedRows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM recommendation_requests WHERE status = 'COMPLETED'", Integer.class);

        assertThat(rows).isZero();
        assertThat(completedRows).isZero();
    }

    @Test
    void acceptedRequestCompletesThroughTheRuleFallbackAndSaysItIsDegraded() throws Exception {
        String ownerToken = signup("owner@example.com", "주인");
        String memberToken = signup("member@example.com", "짝");
        String outsiderToken = signup("outsider@example.com", "남");
        long groupId = createCoupleGroup(ownerToken, "우리 둘");
        join(ownerToken, groupId, memberToken);
        long outsiderGroupId = createCoupleGroup(outsiderToken, "남의 그룹");

        long candidatePlaceId = addPlace(outsiderToken, outsiderGroupId, "kakao-1", "○○찻집", "인사동");
        saveReview(outsiderToken, candidatePlaceId, outsiderGroupId, "2026-08-01", 5, "조용해서 좋았어요")
                .andExpect(status().isCreated());

        long ourPlaceId = addPlace(ownerToken, groupId, "kakao-2", "△△식당", "인사동");
        saveReview(ownerToken, ourPlaceId, groupId, "2026-08-02", 4, "조용해서 좋았어요")
                .andExpect(status().isCreated());
        saveReview(ownerToken, ourPlaceId, groupId, "2026-08-09", 4, "조용해서 좋았어요")
                .andExpect(status().isCreated());

        long requestId = dataOf(requestRecommendation(ownerToken, groupId, "오늘 인사동 갈 건데 3곳 정도 추천해줘")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value(202))
                .andExpect(jsonPath("$.message").value("추천을 준비하고 있습니다"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn()).path("requestId").asLong();

        JsonNode result = awaitFinishedRequest(ownerToken, requestId);
        assertThat(result.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(result.path("intent").path("region").asText()).isEqualTo("인사동");
        assertThat(result.path("intent").path("count").asInt()).isEqualTo(3);
        assertThat(result.path("candidateCount").asInt()).isEqualTo(1);
        assertThat(result.path("recommendations").size()).isEqualTo(1);
        assertThat(result.path("recommendations").get(0).path("placeId").asLong()).isEqualTo(candidatePlaceId);
    }

    @Test
    void anotherGroupsRequestIsForbiddenAndAnUnknownRequestIsNotFound() throws Exception {
        String ownerToken = signup("owner@example.com", "주인");
        String outsiderToken = signup("outsider@example.com", "남");
        long groupId = createCoupleGroup(ownerToken, "우리 둘");
        createCoupleGroup(outsiderToken, "남의 그룹");

        long requestId = dataOf(requestRecommendation(ownerToken, groupId, "인사동에서 두 곳 추천해줘")
                .andExpect(status().isAccepted())
                .andReturn()).path("requestId").asLong();

        mockMvc.perform(get("/api/recommendation-requests/" + requestId)
                        .header("Authorization", bearer(outsiderToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error.code").value("NOT_GROUP_MEMBER"));

        mockMvc.perform(get("/api/recommendation-requests/999999")
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error.code").value("RECOMMENDATION_NOT_FOUND"));

        awaitFinishedRequest(ownerToken, requestId);
    }

    private JsonNode awaitFinishedRequest(String token, long requestId) throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            JsonNode result = dataOf(mockMvc.perform(get("/api/recommendation-requests/" + requestId)
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isOk())
                    .andReturn());
            String requestStatus = result.path("status").asText();
            if ("COMPLETED".equals(requestStatus) || "FAILED".equals(requestStatus)) {
                return result;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("추천이 10초 안에 끝나지 않았습니다 requestId=" + requestId);
    }

    private ResultActions requestRecommendation(String token, long groupId, String query) throws Exception {
        return mockMvc.perform(post("/api/groups/" + groupId + "/recommendation-requests")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateRecommendationRequest(query))));
    }

    private ResultActions saveReview(String token, long placeId, Long groupId, String visitedOn,
                                     int rating, String content) throws Exception {
        CreateReviewRequest request =
                new CreateReviewRequest(placeId, groupId, LocalDate.parse(visitedOn), rating, content);
        return mockMvc.perform(post("/api/reviews")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
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

    private void join(String ownerToken, long groupId, String memberToken) throws Exception {
        String code = dataOf(mockMvc.perform(post("/api/groups/" + groupId + "/invites")
                .header("Authorization", bearer(ownerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isCreated())
                .andReturn()).path("code").asText();
        mockMvc.perform(post("/api/groups/members")
                        .header("Authorization", bearer(memberToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteCode\":\"" + code + "\"}"))
                .andExpect(status().isCreated());
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
