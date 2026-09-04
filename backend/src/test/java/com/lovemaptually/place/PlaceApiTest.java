package com.lovemaptually.place;

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
import com.lovemaptually.support.DatabaseCleanup;
import java.math.BigDecimal;
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
class PlaceApiTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.jwt.secret", () -> "place-test-secret-key-longer-than-32-bytes");
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
     * 담기와 리뷰는 별개 단계입니다. 담기만 하면 라벨이 null이고, 같은 장소를 또 담으면 UNIQUE 제약에서 409가 나옵니다.
     */
    @Test
    void addingANewPlaceReturns201WithNullLabelAndAddingItAgainReturns409() throws Exception {
        String token = signup("map@example.com", "지도사용자");
        long groupId = createCoupleGroup(token, "우리 둘");

        MvcResult added = addPlace(token, groupId, "kakao-1", "○○찻집", "인사동")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.message").value("우리 지도에 담았습니다"))
                .andExpect(jsonPath("$.data.groupId").value(groupId))
                .andReturn();
        JsonNode addedPlace = dataOf(added);
        long placeId = addedPlace.path("placeId").asLong();
        assertThat(addedPlace.path("label").isNull()).isTrue();

        JsonNode marker = dataOf(mockMvc.perform(get("/api/groups/" + groupId + "/places")
                .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.markers[0].placeId").value(placeId))
                .andExpect(jsonPath("$.data.markers[0].reviewedCount").value(0))
                .andReturn()).path("markers").get(0);
        assertThat(marker.path("label").isNull()).isTrue();

        addPlace(token, groupId, "kakao-1", "○○찻집", "인사동")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error.code").value("PLACE_ALREADY_ADDED"));
    }

    @Test
    void placeSearchRequiresAQueryAndReturnsAContentArray() throws Exception {
        String token = signup("search@example.com", "검색사용자");
        long groupId = createCoupleGroup(token, "우리 둘");
        long placeId = dataOf(addPlace(token, groupId, "kakao-1", "○○찻집", "인사동")
                .andExpect(status().isCreated())
                .andReturn()).path("placeId").asLong();

        mockMvc.perform(get("/api/places").param("query", "   ").header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error.code").value("SEARCH_QUERY_REQUIRED"));

        mockMvc.perform(get("/api/places").param("query", "찻집").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content[0].placeId").value(placeId))
                .andExpect(jsonPath("$.data.content[0].name").value("○○찻집"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void unknownPlaceDetailReturns404() throws Exception {
        String token = signup("detail@example.com", "상세사용자");

        mockMvc.perform(get("/api/places/999999").header("Authorization", bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error.code").value("PLACE_NOT_FOUND"));
    }

    @Test
    void groupMapOfAnotherGroupReturns403NotGroupMember() throws Exception {
        String ownerToken = signup("owner@example.com", "주인");
        String outsiderToken = signup("outsider@example.com", "남");
        long groupId = createCoupleGroup(ownerToken, "우리 둘");
        createCoupleGroup(outsiderToken, "남의 그룹");

        mockMvc.perform(get("/api/groups/" + groupId + "/places")
                        .header("Authorization", bearer(outsiderToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error.code").value("NOT_GROUP_MEMBER"));
    }

    private ResultActions addPlace(String token, long groupId, String providerPlaceId, String name, String region)
            throws Exception {
        AddGroupPlaceRequest request = new AddGroupPlaceRequest(null, new PlaceInput(
                "kakao", providerPlaceId, name, "서울시 종로구 " + name, region, "카페", 2,
                new BigDecimal("37.5740000"), new BigDecimal("126.9850000")));
        return mockMvc.perform(post("/api/groups/" + groupId + "/places")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
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
