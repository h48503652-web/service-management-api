package com.example.servicemanagement;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class ServiceManagementApiIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void userEndpointsReturnExpectedHttpStatusesAndJson() throws Exception {
        String email = uniqueEmail();
        String request = "{\"name\":\"API User\",\"email\":\"" + email + "\"}";

        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.email").value(email));

        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Duplicate user email"));

        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"));
    }

    @Test
    void ticketAndCommentEndpointsSerializeEveryResponse() throws Exception {
        long creatorId = createUser("Creator");
        long assigneeId = createUser("Assignee");

        MvcResult createdTicket = mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"VPN access issue\","
                                + "\"description\":\"Remote workers cannot access the VPN.\","
                                + "\"priority\":\"HIGH\","
                                + "\"createdByUserId\":" + creatorId + ","
                                + "\"assignedUserId\":" + creatorId + "}"))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.commentCount").value(0))
                .andExpect(jsonPath("$.createdBy.id").value(creatorId))
                .andReturn();
        long ticketId = readLong(createdTicket, "id");

        mockMvc.perform(get("/api/tickets/{id}", ticketId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("VPN access issue"));

        mockMvc.perform(put("/api/tickets/{id}", ticketId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Updated VPN issue\","
                                + "\"description\":\"Remote workers still cannot access the VPN service.\","
                                + "\"priority\":\"MEDIUM\","
                                + "\"assignedUserId\":" + assigneeId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated VPN issue"))
                .andExpect(jsonPath("$.priority").value("MEDIUM"));

        mockMvc.perform(patch("/api/tickets/{id}/status", ticketId).param("status", "IN_PROGRESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        mockMvc.perform(patch("/api/tickets/{id}/assignment", ticketId).param("userId", Long.toString(creatorId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedUser.email").value(emailForUser(creatorId)));

        mockMvc.perform(post("/api/tickets/{id}/comments", ticketId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"authorEmail\":\"" + emailForUser(creatorId)
                                + "\",\"content\":\"Investigating the access logs.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ticketId").value(ticketId))
                .andExpect(jsonPath("$.authorEmail").value(emailForUser(creatorId)));

        mockMvc.perform(get("/api/tickets/{id}/comments", ticketId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].content").value("Investigating the access logs."));

        mockMvc.perform(get("/api/tickets/{id}", ticketId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commentCount").value(1));

        mockMvc.perform(get("/api/tickets")
                        .param("page", "0")
                        .param("size", "100")
                        .param("sortBy", "createdAt")
                        .param("direction", "DESC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + ticketId + ")].commentCount").value(1));

        mockMvc.perform(delete("/api/tickets/{id}", ticketId))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/tickets/{id}", ticketId))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidPaginationAndSortingReturnBadRequest() throws Exception {
        mockMvc.perform(get("/api/tickets").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("page must be greater than or equal to 0"));

        mockMvc.perform(get("/api/tickets").param("sortBy", "invalidField"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request parameter"));

        mockMvc.perform(get("/api/tickets").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("size must be between 1 and 100"));

        mockMvc.perform(get("/api/tickets").param("direction", "sideways"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("direction must be ASC or DESC"));

        mockMvc.perform(get("/api/tickets/99999/comments").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("page must be greater than or equal to 0"));

        mockMvc.perform(get("/api/tickets/99999"))
                .andExpect(status().isNotFound());
    }

    private long createUser(String name) throws Exception {
        String email = uniqueEmail();
        MvcResult result = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"email\":\"" + email + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return readLong(result, "id");
    }

    private String emailForUser(long id) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/users/{id}", id))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.email");
    }

    private long readLong(MvcResult result, String field) throws Exception {
        Number value = JsonPath.read(result.getResponse().getContentAsString(), "$." + field);
        return value.longValue();
    }

    private String uniqueEmail() {
        return "api-" + UUID.randomUUID() + "@company.com";
    }
}
