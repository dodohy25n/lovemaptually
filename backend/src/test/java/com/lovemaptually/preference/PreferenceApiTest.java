package com.lovemaptually.preference;

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
 * user_tags를 손으로 채우지 않고 리뷰 API로 쌓습니다. 취향은 리뷰에서만 나온다는 것이 설계라서
 * 그 경로를 그대로 지나야 판정이 진짜인지 알 수 있습니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class PreferenceApiTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.jwt.secret", () -> "preference-test-secret-key-longer-than-32-bytes");
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
     * 두 사람이 맵기에서 반대쪽을 두 번씩 원했으면 SPLIT이고 분모는 2입니다.
     * 조용함은 둘 다 같은 쪽을 두 번씩 원했으니 ALL_SAME입니다.
     * 한 번만 쓴 웨이팅은 판정하지 않아 목록에서 아예 빠집니다.
     */
    @Test
    void splitAndAllSameAreJudgedOnlyAfterTwoVotesPerMember() throws Exception {
        String ownerToken = signup("mild@example.com", "순한사람");
        String memberToken = signup("hot@example.com", "매운사람");
        long groupId = createCoupleGroup(ownerToken, "우리 둘");
        join(ownerToken, groupId, memberToken);
        long firstPlaceId = addPlace(ownerToken, groupId, "kakao-1", "○○찻집", "인사동");
        long secondPlaceId = addPlace(ownerToken, groupId, "kakao-2", "△△식당", "인사동");

        saveReview(ownerToken, firstPlaceId, groupId, "2026-09-01", 4,
                "안 매워서 좋았어요 조용해서 좋았어요 웨이팅이 40분이라 힘들었어요").andExpect(status().isCreated());
        saveReview(ownerToken, secondPlaceId, groupId, "2026-09-01", 4,
                "안 매워서 좋았어요 조용해서 좋았어요").andExpect(status().isCreated());
        saveReview(memberToken, firstPlaceId, groupId, "2026-09-01", 4,
                "매워서 좋았어요 조용해서 좋았어요").andExpect(status().isCreated());
        saveReview(memberToken, secondPlaceId, groupId, "2026-09-01", 4,
                "매워서 좋았어요 조용해서 좋았어요").andExpect(status().isCreated());

        JsonNode preferences = dataOf(mockMvc.perform(get("/api/groups/" + groupId + "/preferences")
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.groupId").value(groupId))
                .andExpect(jsonPath("$.data.preferences").isArray())
                .andReturn()).path("preferences");

        JsonNode spice = preference(preferences, "맵기");
        assertThat(spice.path("label").asText()).isEqualTo("SPLIT");
        assertThat(spice.path("judgedMemberCount").asInt()).isEqualTo(2);
        assertThat(spice.path("side").isNull()).isTrue();
        assertThat(spice.path("members").size()).isEqualTo(2);

        JsonNode quiet = preference(preferences, "조용함");
        assertThat(quiet.path("label").asText()).isEqualTo("ALL_SAME");
        assertThat(quiet.path("judgedMemberCount").asInt()).isEqualTo(2);
        assertThat(quiet.path("side").asText()).isEqualTo("HIGH");
        assertThat(quiet.path("sideLabel").asText()).isEqualTo("조용함");

        assertThat(find(preferences, "웨이팅")).isNull();

        long ownerId = jdbcTemplate.queryForObject(
                "SELECT user_id FROM users WHERE email = ?", Long.class, "mild@example.com");
        long waitingTagId = jdbcTemplate.queryForObject(
                "SELECT tag_id FROM tags WHERE name = ?", Long.class, "웨이팅");
        Integer waitingVotes = jdbcTemplate.queryForObject(
                "SELECT want_low_count FROM user_tags WHERE user_id = ? AND tag_id = ?",
                Integer.class, ownerId, waitingTagId);
        assertThat(waitingVotes).isEqualTo(1);
    }

    private JsonNode preference(JsonNode preferences, String tagName) {
        JsonNode found = find(preferences, tagName);
        if (found == null) {
            throw new AssertionError("응답에 " + tagName + " 취향이 없습니다: " + preferences);
        }
        return found;
    }

    private JsonNode find(JsonNode preferences, String tagName) {
        for (JsonNode node : preferences) {
            if (tagName.equals(node.path("tagName").asText())) {
                return node;
            }
        }
        return null;
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
