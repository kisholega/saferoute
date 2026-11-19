package com.saferoute.ai.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.saferoute.ai.IntegrationTestBase;

class EmergencyContactApiTest extends IntegrationTestBase {

    private MockHttpSession session;

    @BeforeEach
    void signIn() throws Exception {
        session = signUp();
    }

    private ResultActions create(String name, String phone, Boolean primary) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("name", name, "phone", phone, "relation", "Friend"));
        if (primary != null) {
            body.put("isPrimary", primary);
        }
        return perform(post("/api/emergency-contacts"), session, body);
    }

    @Test
    void firstContactBecomesPrimaryWithInternationalNumberAndPrivatePushTopic() throws Exception {
        create("Asha", "98765 43210", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.phone").value("+919876543210"))
                .andExpect(jsonPath("$.isPrimary").value(true))
                .andExpect(jsonPath("$.pushTopic").value(Matchers.startsWith("sr-")))
                .andExpect(jsonPath("$.userId").doesNotExist());
    }

    @Test
    void pushTopicsAreUniquePerContact() throws Exception {
        String a = body(create("Asha", "9876543210", null)).path("pushTopic").asText();
        String b = body(create("Ravi", "9876543211", null)).path("pushTopic").asText();
        assertThat(a).isNotEqualTo(b).hasSizeGreaterThan(20);
    }

    @Test
    void acceptsCommonPhoneFormatsAndOptionalEmail() throws Exception {
        create("Amy", "+44 7700 900123", null).andExpect(jsonPath("$.phone").value("+447700900123"));
        create("Bina", "098765-43211", null).andExpect(jsonPath("$.phone").value("+919876543211"));
        perform(post("/api/emergency-contacts"), session,
                Map.of("name", "Chitra", "phone", "0091 98765 43212", "relation", "Sister", "email", "Chitra@Example.test"))
                .andExpect(jsonPath("$.phone").value("+919876543212"))
                .andExpect(jsonPath("$.email").value("chitra@example.test"));
    }

    @Test
    void invalidInputIsReportedOnTheRightField() throws Exception {
        create("Asha", "12345", null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.phone").exists());
        perform(post("/api/emergency-contacts"), session,
                Map.of("name", "Asha", "phone", "9876543210", "relation", "Sister", "email", "not-an-email"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.email").exists());
    }

    @Test
    void sameNumberCannotBeSavedTwiceEvenIfTypedDifferently() throws Exception {
        create("Asha", "98765 43210", null).andExpect(status().isCreated());
        create("Asha again", "+91-98765-43210", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.phone").value(Matchers.containsString("already saved")));
    }

    @Test
    void makingAnotherContactPrimaryUnsetsThePreviousOne() throws Exception {
        create("Asha", "9876543210", null);
        create("Ravi", "9876543211", true).andExpect(jsonPath("$.isPrimary").value(true));
        perform(get("/api/emergency-contacts"), session, null)
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Ravi"))
                .andExpect(jsonPath("$[1].isPrimary").value(false));
    }

    @Test
    void deletingThePrimaryPromotesTheNextContact() throws Exception {
        long asha = body(create("Asha", "9876543210", null)).path("id").asLong();
        create("Ravi", "9876543211", null);
        perform(delete("/api/emergency-contacts/" + asha), session, null).andExpect(status().isNoContent());
        perform(get("/api/emergency-contacts"), session, null)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].isPrimary").value(true));
    }

    @Test
    void updateChangesFields() throws Exception {
        long id = body(create("Asha", "9876543210", null)).path("id").asLong();
        perform(put("/api/emergency-contacts/" + id), session,
                Map.of("name", "Asha K", "phone", "9876500000", "relation", "Sister", "email", "asha@example.test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Asha K"))
                .andExpect(jsonPath("$.phone").value("+919876500000"))
                .andExpect(jsonPath("$.email").value("asha@example.test"));
    }

    @Test
    void enforcesTheContactLimit() throws Exception {
        for (int i = 0; i < 5; i++) {
            create("Person " + i, "98765432" + (10 + i), null).andExpect(status().isCreated());
        }
        create("One too many", "9876543299", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(Matchers.containsString("up to 5")));
    }

    @Test
    void testAlertIsReallyPushedToTheContactsTopicAndReportsEachChannel() throws Exception {
        JsonNode contact = body(create("Asha", "9876543210", null));
        JsonNode alert = body(perform(post("/api/emergency-contacts/" + contact.path("id").asLong() + "/test"), session, null)
                .andExpect(status().isOk()));

        assertThat(alert.path("type").asText()).isEqualTo("TEST");
        assertThat(pushes).hasSize(1);
        alert = body(perform(get("/api/alerts/" + alert.path("id").asLong()), session, null)); // delivery runs after commit
        assertThat(pushes.get(0)).startsWith(contact.path("pushTopic").asText() + "|").contains("Test alert");
        assertThat(alert.path("deliveries").toString())
                .contains("\"channel\":\"PUSH\",\"status\":\"SENT\"")
                .contains("\"channel\":\"EMAIL\",\"status\":\"SKIPPED\"")
                .contains("\"channel\":\"SMS\",\"status\":\"SKIPPED\"");
    }

    @Test
    void unknownContactIs404() throws Exception {
        perform(delete("/api/emergency-contacts/999999"), session, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Emergency contact not found."));
    }
}
