package com.carent.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carent.auth.TestSecrets;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.SignedJWT;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** specs/05: POST /api/v1/auth/token returns a JWT; specs/07: errors are RFC 7807 ProblemDetail. */
@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class AuthControllerTest {

    private static final String TOKEN = "/api/v1/auth/token";
    private static final String SECRET = TestSecrets.jwtSecret();
    private static final String MEMBER_PASSWORD = TestSecrets.password();
    private static final String ADJUDICATOR_PASSWORD = TestSecrets.password();

    @DynamicPropertySource
    static void authProperties(DynamicPropertyRegistry registry) {
        registry.add("auth.jwt.secret", () -> SECRET);
        registry.add("auth.users[0].username", () -> "member1");
        registry.add("auth.users[0].password", () -> MEMBER_PASSWORD);
        registry.add("auth.users[0].roles[0]", () -> "ROLE_MEMBER");
        registry.add("auth.users[1].username", () -> "adjudicator1");
        registry.add("auth.users[1].password", () -> ADJUDICATOR_PASSWORD);
        registry.add("auth.users[1].roles[0]", () -> "ROLE_CLAIMS_ADJUDICATOR");
        registry.add("auth.users[1].roles[1]", () -> "ROLE_MEMBER");
    }

    private final MockMvc mvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    AuthControllerTest(MockMvc mvc) {
        this.mvc = mvc;
    }

    private ResultActions login(String username, String password) throws Exception {
        return mvc.perform(post(TOKEN).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("username", username, "password", password))));
    }

    private ResultActions loginRaw(String body) throws Exception {
        return mvc.perform(post(TOKEN).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void valid_credentials_return_a_bearer_token_verifiable_with_the_shared_secret() throws Exception {
        String body = login("adjudicator1", ADJUDICATOR_PASSWORD).andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andReturn().getResponse().getContentAsString();

        SignedJWT jwt = SignedJWT.parse(objectMapper.readTree(body).get("accessToken").asText());
        assertThat(jwt.verify(new MACVerifier(SECRET))).isTrue();
        assertThat(jwt.getJWTClaimsSet().getSubject()).isEqualTo("adjudicator1");
        assertThat(jwt.getJWTClaimsSet().getStringListClaim("roles"))
                .containsExactly("ROLE_CLAIMS_ADJUDICATOR", "ROLE_MEMBER");
    }

    @Test
    void member_gets_only_the_member_role() throws Exception {
        String body = login("member1", MEMBER_PASSWORD).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        SignedJWT jwt = SignedJWT.parse(objectMapper.readTree(body).get("accessToken").asText());
        assertThat(jwt.getJWTClaimsSet().getStringListClaim("roles")).containsExactly("ROLE_MEMBER");
    }

    @Test
    void token_responses_are_never_cached() throws Exception {
        login("member1", MEMBER_PASSWORD).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
    }

    @Test
    void wrong_password_and_unknown_user_give_the_same_generic_problem_detail() throws Exception {
        String wrongPassword = login("member1", TestSecrets.password()).andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.detail").value("Invalid username or password"))
                .andExpect(jsonPath("$.instance").value(TOKEN))
                .andReturn().getResponse().getContentAsString();
        String unknownUser = login("nobody", TestSecrets.password()).andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(unknownUser).isEqualTo(wrongPassword);
        assertThat(wrongPassword).doesNotContain("member1").doesNotContain("nobody");
    }

    @Test
    void blank_username_or_password_is_a_validation_failure() throws Exception {
        loginRaw("{\"username\":\"\",\"password\":\"\"}").andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("password")))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("username")));
    }

    @Test
    void missing_fields_are_a_validation_failure() throws Exception {
        loginRaw("{}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.title").value("Validation Failed"));
    }

    @Test
    void malformed_body_is_a_problem_detail_that_does_not_echo_the_input() throws Exception {
        String response = loginRaw("{\"username\": \"member1\", broken").andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andReturn().getResponse().getContentAsString();

        JsonNode problem = objectMapper.readTree(response);
        assertThat(problem.get("title").asText()).isEqualTo("Validation Failed");
        assertThat(response).doesNotContain("broken").doesNotContain("member1");
    }

    @Test
    void get_on_the_token_endpoint_is_405_problem_detail() throws Exception {
        mvc.perform(get(TOKEN)).andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void the_password_is_never_present_in_any_response() throws Exception {
        String ok = login("member1", MEMBER_PASSWORD).andReturn().getResponse().getContentAsString();
        String bad = login("member1", ADJUDICATOR_PASSWORD).andReturn().getResponse().getContentAsString();

        assertThat(ok).doesNotContain(MEMBER_PASSWORD);
        assertThat(bad).doesNotContain(ADJUDICATOR_PASSWORD);
    }

    @Test
    void actuator_health_is_up() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }
}
