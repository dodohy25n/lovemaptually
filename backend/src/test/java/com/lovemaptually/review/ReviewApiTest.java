package com.lovemaptually.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
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

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ReviewApiTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.jwt.secret", () -> "review-test-secret-key-longer-than-32-bytes");
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

    /**
     * 설계의 중심 주장입니다. 같은 문장에서 가게가 어떤 곳인지(fact)와 그 사람이 무엇을 원하는지(want)를 따로 판정합니다.
     * 웨이팅은 길었지만(fact 김) 그래서 짧은 곳을 원하고(want 짧음),
     * 맵기는 순했고(fact 순함) 그게 좋았으니 순한 곳을 원합니다(want 순함).
     */
    @Test
    void factAndWantAreJudgedSeparatelyWithinOneSentence() throws Exception {
        String token = signup("writer@example.com", "리뷰어");
        long groupId = createCoupleGroup(token, "우리 둘");
        long placeId = addPlace(token, groupId, "kakao-1", "○○찻집", "인사동");

        MvcResult result = saveReview(token, placeId, groupId, "2026-09-01", 4,
                "웨이팅이 40분이라 힘들었는데 안 매워서 좋았어요. 조용해서 얘기하기 좋았어요")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.message").value("리뷰를 저장했습니다"))
                .andExpect(jsonPath("$.data.tagStatus").value("COMPLETED"))
                .andReturn();

        JsonNode data = dataOf(result);
        assertThat(data.path("placeId").asLong()).isEqualTo(placeId);
        assertThat(data.path("rating").asInt()).isEqualTo(4);

        JsonNode waiting = tag(data, "웨이팅");
        assertThat(waiting.path("fact").asText()).isEqualTo("김");
        assertThat(waiting.path("want").asText()).isEqualTo("짧음");

        JsonNode spice = tag(data, "맵기");
        assertThat(spice.path("fact").asText()).isEqualTo("순함");
        assertThat(spice.path("want").asText()).isEqualTo("순함");
    }

    @Test
    void samePlaceOnSameVisitedOnTwiceReturns409ReviewDuplicated() throws Exception {
        String token = signup("dup@example.com", "중복사용자");
        long groupId = createCoupleGroup(token, "우리 둘");
        long placeId = addPlace(token, groupId, "kakao-1", "○○찻집", "인사동");

        saveReview(token, placeId, groupId, "2026-09-01", 4, "조용해서 좋았어요")
                .andExpect(status().isCreated());

        saveReview(token, placeId, groupId, "2026-09-01", 5, "또 조용해서 좋았어요")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error.code").value("REVIEW_DUPLICATED"))
                .andExpect(jsonPath("$.error.details").isArray());
    }

    @Test
    void ratingOutsideOneToFiveReturns422RatingOutOfRange() throws Exception {
        String token = signup("rating@example.com", "별점사용자");
        long groupId = createCoupleGroup(token, "우리 둘");
        long placeId = addPlace(token, groupId, "kakao-1", "○○찻집", "인사동");

        saveReview(token, placeId, groupId, "2026-09-01", 9, "조용해서 좋았어요")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.error.code").value("RATING_OUT_OF_RANGE"))
                .andExpect(jsonPath("$.error.details", hasSize(0)));

        saveReview(token, placeId, groupId, "2026-09-02", 0, "조용해서 좋았어요")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.error.code").value("RATING_OUT_OF_RANGE"));
    }

    @Test
    void withGroupIdOfAnotherGroupReturns403NotGroupMember() throws Exception {
        String token = signup("mine@example.com", "나");
        String outsiderToken = signup("theirs@example.com", "남");
        long myGroupId = createCoupleGroup(token, "우리 둘");
        long otherGroupId = createCoupleGroup(outsiderToken, "남의 그룹");
        long placeId = addPlace(token, myGroupId, "kakao-1", "○○찻집", "인사동");

        saveReview(token, placeId, otherGroupId, "2026-09-01", 4, "조용해서 좋았어요")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error.code").value("NOT_GROUP_MEMBER"));
    }

    @Test
    void unknownPlaceIdReturns404PlaceNotFound() throws Exception {
        String token = signup("nowhere@example.com", "없는장소");
        long groupId = createCoupleGroup(token, "우리 둘");

        saveReview(token, 999_999L, groupId, "2026-09-01", 4, "조용해서 좋았어요")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error.code").value("PLACE_NOT_FOUND"));
    }

    @Test
    void missingAuthorizationHeaderReturns401UnauthorizedEnvelope() throws Exception {
        CreateReviewRequest request =
                new CreateReviewRequest(1L, null, LocalDate.parse("2026-09-01"), 4, "조용해서 좋았어요");

        mockMvc.perform(post("/api/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.error.details", hasSize(0)));
    }

    @Test
    void reviewDetailIsReadableByItsAuthorOnly() throws Exception {
        String ownerToken = signup("author@example.com", "글쓴이");
        String otherToken = signup("reader@example.com", "남의사람");
        long groupId = createCoupleGroup(ownerToken, "우리 둘");
        createCoupleGroup(otherToken, "남의 그룹");
        long placeId = addPlace(ownerToken, groupId, "kakao-1", "○○찻집", "인사동");

        long reviewId = dataOf(saveReview(ownerToken, placeId, groupId, "2026-09-01", 4, "조용해서 좋았어요")
                .andExpect(status().isCreated())
                .andReturn()).path("reviewId").asLong();

        mockMvc.perform(get("/api/reviews/" + reviewId).header("Authorization", bearer(otherToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error.code").value("REVIEW_ACCESS_DENIED"));

        mockMvc.perform(get("/api/reviews/" + reviewId).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.reviewId").value(reviewId))
                .andExpect(jsonPath("$.data.content", not(blankOrNullString())));
    }

    /**
     * 1인 1표입니다. 한 사람이 같은 장소를 두 번 다녀와 두 번 다 맵다고 적어도
     * 그 장소가 매운 곳이라는 표는 한 표이고, 그 사람이 매운 곳을 원한다는 표는 두 표입니다.
     */
    @Test
    void placeTagCountsOneVotePerPersonWhileUserTagCountsEveryReview() throws Exception {
        String token = signup("twice@example.com", "재방문자");
        long groupId = createCoupleGroup(token, "우리 둘");
        long placeId = addPlace(token, groupId, "kakao-1", "△△식당", "인사동");

        saveReview(token, placeId, groupId, "2026-09-01", 5, "매워서 좋았어요").andExpect(status().isCreated());
        saveReview(token, placeId, groupId, "2026-09-15", 5, "매워서 좋았어요").andExpect(status().isCreated());

        long userId = jdbcTemplate.queryForObject(
                "SELECT user_id FROM users WHERE email = ?", Long.class, "twice@example.com");
        long spiceTagId = jdbcTemplate.queryForObject(
                "SELECT tag_id FROM tags WHERE name = ?", Long.class, "맵기");

        Integer placeVotes = jdbcTemplate.queryForObject(
                "SELECT fact_high_count FROM place_tags WHERE place_id = ? AND tag_id = ?",
                Integer.class, placeId, spiceTagId);
        Integer userVotes = jdbcTemplate.queryForObject(
                "SELECT want_high_count FROM user_tags WHERE user_id = ? AND tag_id = ?",
                Integer.class, userId, spiceTagId);
        Integer reviewRows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM reviews WHERE user_id = ? AND place_id = ?",
                Integer.class, userId, placeId);

        assertThat(reviewRows).isEqualTo(2);
        assertThat(placeVotes).isEqualTo(1);
        assertThat(userVotes).isEqualTo(2);
    }

    /**
     * 라벨의 분모는 그룹 인원이 아니라 리뷰를 쓴 구성원 수입니다.
     * 그리고 내가 쓰기 전에는 남의 리뷰가 잠기는데, 그것은 권한이 아니라 상태라서 200입니다.
     */
    @Test
    void labelDenominatorIsReviewedMembersAndOtherReviewsUnlockAfterMyOwn() throws Exception {
        String ownerToken = signup("owner@example.com", "먼저쓴사람");
        String memberToken = signup("member@example.com", "나중쓴사람");
        long groupId = createCoupleGroup(ownerToken, "우리 둘");
        join(ownerToken, groupId, memberToken);
        long placeId = addPlace(ownerToken, groupId, "kakao-1", "○○찻집", "인사동");

        saveReview(ownerToken, placeId, groupId, "2026-09-01", 4, "조용해서 좋았어요")
                .andExpect(status().isCreated());

        JsonNode lockedView = dataOf(groupReviews(memberToken, groupId, placeId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andReturn());
        assertThat(lockedView.path("placeLabel").asText()).isEqualTo("ALL_LIKED");
        assertThat(lockedView.path("reviewedCount").asInt()).isEqualTo(1);
        assertThat(lockedView.path("likedCount").asInt()).isEqualTo(1);
        assertThat(lockedView.path("otherReviewsLocked").asBoolean()).isTrue();
        assertThat(lockedView.path("lockedReason").isNull()).isFalse();
        assertThat(lockedView.path("myReview").isNull()).isTrue();
        assertThat(lockedView.path("otherReviews").size()).isZero();

        saveReview(memberToken, placeId, groupId, "2026-09-02", 2, "시끄러워서 아쉬웠어요")
                .andExpect(status().isCreated());

        JsonNode memberView = dataOf(groupReviews(memberToken, groupId, placeId)
                .andExpect(status().isOk())
                .andReturn());
        assertThat(memberView.path("placeLabel").asText()).isEqualTo("MIXED");
        assertThat(memberView.path("reviewedCount").asInt()).isEqualTo(2);
        assertThat(memberView.path("likedCount").asInt()).isEqualTo(1);
        assertThat(memberView.path("otherReviewsLocked").asBoolean()).isFalse();
        assertThat(memberView.path("lockedReason").isNull()).isTrue();
        assertThat(memberView.path("otherReviews").size()).isEqualTo(1);

        JsonNode ownerView = dataOf(groupReviews(ownerToken, groupId, placeId)
                .andExpect(status().isOk())
                .andReturn());
        assertThat(ownerView.path("placeLabel").asText()).isEqualTo("MIXED");
        assertThat(ownerView.path("reviewedCount").asInt()).isEqualTo(2);
        assertThat(ownerView.path("otherReviewsLocked").asBoolean()).isFalse();
        assertThat(ownerView.path("otherReviews").size()).isEqualTo(1);
    }

    private ResultActions groupReviews(String token, long groupId, long placeId) throws Exception {
        return mockMvc.perform(get("/api/groups/" + groupId + "/places/" + placeId + "/reviews")
                .header("Authorization", bearer(token)));
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

    private JsonNode tag(JsonNode data, String tagName) {
        for (JsonNode node : data.path("tags")) {
            if (tagName.equals(node.path("tag").asText())) {
                return node;
            }
        }
        throw new AssertionError("응답에 " + tagName + " 태그가 없습니다: " + data.path("tags"));
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
