package com.lovemaptually.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 라이터는 기본값인 template이라 테스트에서 외부 호출이 일어나지 않습니다. OpenAI 키는 넣지 않습니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ReportApiTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.jwt.secret", () -> "report-test-secret-key-longer-than-32-bytes");
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
    void freeGroupRequestingAReportReturns402PlanRequired() throws Exception {
        String token = signup("free@example.com", "무료그룹");
        long groupId = createCoupleGroup(token, "우리 둘");

        requestReport(token, groupId, "2025-03")
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.status").value(402))
                .andExpect(jsonPath("$.error.code").value("PLAN_REQUIRED"))
                .andExpect(jsonPath("$.error.details[0].field").value("plan"));
    }

    @Test
    void mockPaymentUpgradesTheGroupOnceAndRejectsTheSecondAttempt() throws Exception {
        String token = signup("pay@example.com", "결제자");
        long groupId = createCoupleGroup(token, "우리 둘");

        subscribe(token, groupId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.data.groupId").value(groupId))
                .andExpect(jsonPath("$.data.plan").value("PREMIUM"))
                .andExpect(jsonPath("$.data.paymentRef", startsWith("MOCK-")));

        subscribe(token, groupId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error.code").value("ALREADY_PREMIUM"));
    }

    @Test
    void premiumGroupWithNoVisitsInThatMonthReturns422NoVisitsInMonth() throws Exception {
        String token = signup("empty@example.com", "빈달");
        long groupId = createCoupleGroup(token, "우리 둘");
        subscribe(token, groupId).andExpect(status().isCreated());

        requestReport(token, groupId, "2025-03")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.error.code").value("NO_VISITS_IN_MONTH"));
    }

    @Test
    void reportIsAcceptedAsPendingAndCompletedByTheWorker() throws Exception {
        String token = signup("writer@example.com", "리포트작성");
        long groupId = createCoupleGroup(token, "우리 둘");
        subscribe(token, groupId).andExpect(status().isCreated());
        long placeId = addPlace(token, groupId, "kakao-1", "○○찻집", "인사동");
        saveReview(token, placeId, groupId, "2025-03-10", 5, "조용해서 좋았어요")
                .andExpect(status().isCreated());

        long reportId = dataOf(requestReport(token, groupId, "2025-03")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value(202))
                .andExpect(jsonPath("$.message").value("리포트를 쓰고 있습니다"))
                .andExpect(jsonPath("$.data.groupId").value(groupId))
                .andExpect(jsonPath("$.data.reportMonth").value("2025-03-01"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn()).path("reportId").asLong();

        JsonNode report = awaitFinishedReport(token, reportId);
        assertThat(report.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(report.path("content").path("title").isMissingNode()).isFalse();
        assertThat(report.path("content").path("title").asText()).isNotBlank();
        assertThat(report.path("content").path("summary").asText()).isNotBlank();
        assertThat(report.path("content").path("meta").path("discarded").isMissingNode()).isFalse();
        assertThat(report.path("content").path("meta").path("discarded").asInt()).isZero();
    }

    @Test
    void sameMonthRequestedTwiceReturns409WithTheExistingReportId() throws Exception {
        String token = signup("twice@example.com", "두번요청");
        long groupId = createCoupleGroup(token, "우리 둘");
        subscribe(token, groupId).andExpect(status().isCreated());
        long placeId = addPlace(token, groupId, "kakao-1", "○○찻집", "인사동");
        saveReview(token, placeId, groupId, "2025-03-10", 5, "조용해서 좋았어요")
                .andExpect(status().isCreated());

        long reportId = dataOf(requestReport(token, groupId, "2025-03")
                .andExpect(status().isAccepted())
                .andReturn()).path("reportId").asLong();
        assertThat(awaitFinishedReport(token, reportId).path("status").asText()).isEqualTo("COMPLETED");

        requestReport(token, groupId, "2025-03")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error.code").value("REPORT_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.error.details[0].field").value("month"))
                .andExpect(jsonPath("$.error.details[0].reason").value("reportId=" + reportId));
    }

    /**
     * 월 경계는 visitedOn으로 판정합니다. 말일 방문은 그 달에 들어가고 다음 달 1일 방문은 들어가지 않습니다.
     * 오늘 날짜와 무관하게 성립하도록 고정된 과거 달로 확인합니다.
     */
    @Test
    void lastDayOfMonthCountsForThatMonthAndFirstDayOfNextMonthDoesNot() throws Exception {
        String token = signup("boundary@example.com", "월경계");
        long groupId = createCoupleGroup(token, "우리 둘");
        subscribe(token, groupId).andExpect(status().isCreated());
        long placeId = addPlace(token, groupId, "kakao-1", "○○찻집", "인사동");

        saveReview(token, placeId, groupId, "2025-04-01", 5, "조용해서 좋았어요")
                .andExpect(status().isCreated());

        requestReport(token, groupId, "2025-03")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("NO_VISITS_IN_MONTH"));
        long aprilReportId = dataOf(requestReport(token, groupId, "2025-04")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.reportMonth").value("2025-04-01"))
                .andReturn()).path("reportId").asLong();

        saveReview(token, placeId, groupId, "2025-03-31", 5, "조용해서 좋았어요")
                .andExpect(status().isCreated());

        long marchReportId = dataOf(requestReport(token, groupId, "2025-03")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.reportMonth").value("2025-03-01"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn()).path("reportId").asLong();

        assertThat(awaitFinishedReport(token, aprilReportId).path("status").asText()).isEqualTo("COMPLETED");
        assertThat(awaitFinishedReport(token, marchReportId).path("status").asText()).isEqualTo("COMPLETED");
    }

    @Test
    void nonMemberIsForbiddenAndUnknownReportIdIsNotFound() throws Exception {
        String ownerToken = signup("owner@example.com", "그룹주인");
        String outsiderToken = signup("outsider@example.com", "남");
        long groupId = createCoupleGroup(ownerToken, "우리 둘");
        createCoupleGroup(outsiderToken, "남의 그룹");

        mockMvc.perform(get("/api/groups/" + groupId + "/reports")
                        .header("Authorization", bearer(outsiderToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error.code").value("NOT_GROUP_MEMBER"));

        mockMvc.perform(get("/api/reports/999999").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error.code").value("REPORT_NOT_FOUND"));
    }

    @Test
    void reportListReturnsThePlanAndTheReportsArray() throws Exception {
        String token = signup("list@example.com", "목록");
        long groupId = createCoupleGroup(token, "우리 둘");

        mockMvc.perform(get("/api/groups/" + groupId + "/reports").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.plan").value("FREE"))
                .andExpect(jsonPath("$.data.reports").isArray());

        subscribe(token, groupId).andExpect(status().isCreated());
        long placeId = addPlace(token, groupId, "kakao-1", "○○찻집", "인사동");
        saveReview(token, placeId, groupId, "2025-03-10", 5, "조용해서 좋았어요")
                .andExpect(status().isCreated());
        long reportId = dataOf(requestReport(token, groupId, "2025-03")
                .andExpect(status().isAccepted())
                .andReturn()).path("reportId").asLong();
        awaitFinishedReport(token, reportId);

        mockMvc.perform(get("/api/groups/" + groupId + "/reports").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.plan").value("PREMIUM"))
                .andExpect(jsonPath("$.data.reports[0].reportId").value(reportId))
                .andExpect(jsonPath("$.data.reports[0].reportMonth").value("2025-03-01"));
    }

    private JsonNode awaitFinishedReport(String token, long reportId) throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            JsonNode report = dataOf(mockMvc.perform(get("/api/reports/" + reportId)
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isOk())
                    .andReturn());
            String reportStatus = report.path("status").asText();
            if ("COMPLETED".equals(reportStatus) || "FAILED".equals(reportStatus)) {
                return report;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("리포트가 10초 안에 끝나지 않았습니다 reportId=" + reportId);
    }

    private ResultActions requestReport(String token, long groupId, String month) throws Exception {
        return mockMvc.perform(post("/api/groups/" + groupId + "/reports")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"month\":\"" + month + "\"}"));
    }

    private ResultActions subscribe(String token, long groupId) throws Exception {
        return mockMvc.perform(post("/api/groups/" + groupId + "/subscriptions")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"plan\":\"PREMIUM\"}"));
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
