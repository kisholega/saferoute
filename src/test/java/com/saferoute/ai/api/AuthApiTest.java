package com.saferoute.ai.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import com.saferoute.ai.IntegrationTestBase;

class AuthApiTest extends IntegrationTestBase {

    private Map<String, Object> registration(String email) {
        return Map.of("name", "Asha Rao", "email", email, "phone", "98765 43210", "password", "s3cure-pass");
    }

    @Test
    void registerSignsInAndNeverExposesThePasswordHash() throws Exception {
        var result = perform(post("/api/auth/register"), new MockHttpSession(), registration("asha@example.test"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Asha Rao"))
                .andExpect(jsonPath("$.phone").value("+919876543210"));
        assertThat(result.andReturn().getResponse().getContentAsString()).doesNotContain("password").doesNotContain("$2a$");
        MockHttpSession session = sessionOf(result);

        perform(get("/api/auth/me"), session, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("asha@example.test"));
    }

    @Test
    void duplicateEmailIsRejectedOnTheEmailField() throws Exception {
        perform(post("/api/auth/register"), new MockHttpSession(), registration("dup@example.test")).andExpect(status().isCreated());
        perform(post("/api/auth/register"), new MockHttpSession(), registration("DUP@example.test"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists());
    }

    @Test
    void weakInputIsValidated() throws Exception {
        perform(post("/api/auth/register"), new MockHttpSession(),
                Map.of("name", "A", "email", "not-an-email", "phone", "123", "password", "short"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void loginWorksWithCorrectPasswordOnly() throws Exception {
        perform(post("/api/auth/register"), new MockHttpSession(), registration("login@example.test")).andExpect(status().isCreated());

        perform(post("/api/auth/login"), new MockHttpSession(), Map.of("email", "login@example.test", "password", "wrong-pass"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Email or password is incorrect."));

        MockHttpSession session = sessionOf(
                perform(post("/api/auth/login"), new MockHttpSession(), Map.of("email", " Login@Example.test ", "password", "s3cure-pass"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.name").value("Asha Rao")));
        perform(get("/api/auth/me"), session, null).andExpect(status().isOk());
    }

    @Test
    void repeatedWrongPasswordsLockTheAccountBriefly() throws Exception {
        perform(post("/api/auth/register"), new MockHttpSession(), registration("lock@example.test")).andExpect(status().isCreated());
        for (int i = 0; i < 5; i++) {
            perform(post("/api/auth/login"), new MockHttpSession(), Map.of("email", "lock@example.test", "password", "nope-" + i))
                    .andExpect(status().isUnauthorized());
        }
        perform(post("/api/auth/login"), new MockHttpSession(), Map.of("email", "lock@example.test", "password", "s3cure-pass"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void logoutEndsTheSession() throws Exception {
        MockHttpSession session = signUp();
        perform(post("/api/auth/logout"), session, null).andExpect(status().isNoContent());
        perform(get("/api/auth/me"), new MockHttpSession(), null).andExpect(status().isUnauthorized());
    }

    @Test
    void profileCanBeUpdated() throws Exception {
        MockHttpSession session = signUp();
        perform(put("/api/auth/me"), session, Map.of("name", "Priya S", "phone", "+91 90000 12345"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Priya S"))
                .andExpect(jsonPath("$.phone").value("+919000012345"));
    }

    @Test
    void privateApisRequireSignIn() throws Exception {
        MockHttpSession anonymous = new MockHttpSession();
        for (String path : new String[] {"/api/emergency-contacts", "/api/routes/history", "/api/trips", "/api/alerts"}) {
            perform(get(path), anonymous, null)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Please sign in to continue."));
        }
        perform(post("/api/reports"), anonymous, Map.of("latitude", 12.97, "longitude", 77.59, "category", "POOR_LIGHTING"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void usersCannotSeeEachOthersContacts() throws Exception {
        MockHttpSession asha = signUp("Asha");
        MockHttpSession ravi = signUp("Ravi");
        long id = body(perform(post("/api/emergency-contacts"), asha,
                Map.of("name", "Mom", "phone", "9876543210", "relation", "Mother"))).path("id").asLong();

        perform(get("/api/emergency-contacts"), ravi, null).andExpect(jsonPath("$.length()").value(0));
        perform(put("/api/emergency-contacts/" + id), ravi,
                Map.of("name", "Hacked", "phone", "9876543210", "relation", "x")).andExpect(status().isNotFound());
    }
}
