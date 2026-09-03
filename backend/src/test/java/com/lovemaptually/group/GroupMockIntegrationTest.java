package com.lovemaptually.group;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lovemaptually.auth.dto.request.SignupRequest;
import com.lovemaptually.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class GroupMockIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.jwt.secret", () -> "group-test-secret-key-longer-than-32-bytes");
        registry.add("app.jwt.issuer", () -> "lovemaptually-test");
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRepository userRepository;

    @BeforeEach
    void cleanUsers() {
        userRepository.deleteAll();
    }

    @Test
    void groupAndInviteFlowKeepsApiContractAndRealJwtAuthorization() throws Exception {
        String ownerToken = signup("owner@example.com", "owner-user");
        String memberToken = signup("member@example.com", "member-user");
        String outsiderToken = signup("outsider@example.com", "outsider-user");

        JsonNode group = responseData(mockMvc.perform(post("/api/groups")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"groupType\":\"COUPLE\",\"name\":\"우리 둘\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.data.members", hasSize(1)))
                .andExpect(jsonPath("$.data.members[0].role").value("OWNER"))
                .andReturn().getResponse().getContentAsString());
        long groupId = group.path("groupId").asLong();

        mockMvc.perform(post("/api/groups/" + groupId + "/invites")
                        .header("Authorization", bearer(outsiderToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("GROUP_ACCESS_DENIED"));

        JsonNode invite = responseData(mockMvc.perform(post("/api/groups/" + groupId + "/invites")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.maxUses").value(1))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString());
        String code = invite.path("code").asText();

        mockMvc.perform(get("/api/invites/" + code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.groupId").value(groupId))
                .andExpect(jsonPath("$.data.available").value(true));

        mockMvc.perform(post("/api/groups/members")
                        .header("Authorization", bearer(memberToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteCode\":\"" + code.toLowerCase() + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.members", hasSize(2)))
                .andExpect(jsonPath("$.data.members[1].role").value("MEMBER"));

        mockMvc.perform(get("/api/groups/me").header("Authorization", bearer(memberToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.groups", hasSize(1)))
                .andExpect(jsonPath("$.data.groups[0].members", hasSize(2)));

        mockMvc.perform(post("/api/groups/members")
                        .header("Authorization", bearer(outsiderToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteCode\":\"" + code + "\"}"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.error.code").value("INVITE_UNAVAILABLE"));

        mockMvc.perform(post("/api/groups")
                        .header("Authorization", bearer(memberToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"groupType\":\"COUPLE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("COUPLE_GROUP_ALREADY_EXISTS"));
    }

    @Test
    void invalidGroupTypeReturns400AndInvitePreviewIsPublic() throws Exception {
        String token = signup("validation@example.com", "validation-user");

        mockMvc.perform(post("/api/groups")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"groupType\":\"INVALID\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST_BODY"));

        mockMvc.perform(get("/api/invites/DOES-NOT-EXIST"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("INVITE_NOT_FOUND"));
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

    private JsonNode responseData(String body) throws Exception {
        return objectMapper.readTree(body).path("data");
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
