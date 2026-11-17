package com.example.servicemanagement;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that every ticket update returns the {@code updatedAt} value that was actually persisted.
 * The test is not transactional: each request commits its own transaction, as in production,
 * so the follow-up GET reads the stored value instead of an entity that is still in memory.
 */
@SpringBootTest
class TicketUpdatedAtIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void updatingTicketDetailsReturnsPersistedUpdatedAt() throws Exception {
        long userId = createUser();
        long ticketId = createTicket(userId);

        String response = perform(put("/api/tickets/{id}", ticketId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Updated title\","
                        + "\"description\":\"Updated description for the ticket.\","
                        + "\"priority\":\"LOW\","
                        + "\"assignedUserId\":" + userId + "}"), status().isOk());

        assertThat(JsonPath.<String>read(response, "$.title")).isEqualTo("Updated title");
        assertUpdatedAtMatchesStoredTicket(ticketId, response);
    }

    @Test
    void changingTicketStatusReturnsPersistedUpdatedAt() throws Exception {
        long ticketId = createTicket(createUser());

        String response = perform(patch("/api/tickets/{id}/status", ticketId)
                .param("status", "IN_PROGRESS"), status().isOk());

        assertThat(JsonPath.<String>read(response, "$.status")).isEqualTo("IN_PROGRESS");
        assertUpdatedAtMatchesStoredTicket(ticketId, response);
    }

    @Test
    void reassigningTicketReturnsPersistedUpdatedAt() throws Exception {
        long ticketId = createTicket(createUser());
        long newAssigneeId = createUser();

        String response = perform(patch("/api/tickets/{id}/assignment", ticketId)
                .param("userId", Long.toString(newAssigneeId)), status().isOk());

        assertThat(JsonPath.<Number>read(response, "$.assignedUser.id").longValue()).isEqualTo(newAssigneeId);
        assertUpdatedAtMatchesStoredTicket(ticketId, response);
    }

    /**
     * The update response and a following GET must report the same timestamp.
     * Comparing the two values, rather than checking that time has advanced,
     * keeps the test independent of how fast the requests run.
     */
    private void assertUpdatedAtMatchesStoredTicket(long ticketId, String updateResponse) throws Exception {
        String storedTicket = perform(get("/api/tickets/{id}", ticketId), status().isOk());

        assertThat(updatedAt(updateResponse)).isEqualTo(updatedAt(storedTicket));
    }

    private LocalDateTime updatedAt(String ticketJson) {
        return LocalDateTime.parse(JsonPath.<String>read(ticketJson, "$.updatedAt"));
    }

    private long createUser() throws Exception {
        String response = perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Timestamp User\",\"email\":\"updated-at-" + UUID.randomUUID() + "@company.com\"}"),
                status().isCreated());
        return JsonPath.<Number>read(response, "$.id").longValue();
    }

    private long createTicket(long userId) throws Exception {
        String response = perform(post("/api/tickets")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Timestamp ticket\","
                        + "\"description\":\"Ticket used to verify the updatedAt value.\","
                        + "\"priority\":\"MEDIUM\","
                        + "\"createdByUserId\":" + userId + ","
                        + "\"assignedUserId\":" + userId + "}"), status().isCreated());
        return JsonPath.<Number>read(response, "$.id").longValue();
    }

    private String perform(RequestBuilder request, ResultMatcher expectedStatus) throws Exception {
        return mockMvc.perform(request)
                .andExpect(expectedStatus)
                .andReturn()
                .getResponse()
                .getContentAsString();
    }
}
