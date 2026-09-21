package com.legacyforge.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void register_then_login_then_whoami() throws Exception {
        String email = "flow-test-" + System.nanoTime() + "@example.com";
        String body = json.writeValueAsString(new Register(email, "password12345"));

        MvcResult reg = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode regJson = json.readTree(reg.getResponse().getContentAsString());
        String accessToken = regJson.get("accessToken").asText();
        assertThat(accessToken).isNotBlank();
        assertThat(regJson.get("user").get("email").asText()).isEqualTo(email);

        // whoami with the access token
        mvc.perform(get("/api/auth/whoami")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    @Test
    void login_with_wrong_password_returns_401() throws Exception {
        String email = "bad-pw-" + System.nanoTime() + "@example.com";
        String reg = json.writeValueAsString(new Register(email, "password12345"));
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reg))
                .andExpect(status().isCreated());

        String login = json.writeValueAsString(new Register(email, "wrong-password"));
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(login))
                .andExpect(status().isUnauthorized());
    }

    record Register(String email, String password) {}
}
