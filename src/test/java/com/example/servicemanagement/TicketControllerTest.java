package com.example.servicemanagement;

import com.example.servicemanagement.dto.request.CreateTicketRequest;
import com.example.servicemanagement.dto.request.CreateUserRequest;
import com.example.servicemanagement.dto.response.TicketResponse;
import com.example.servicemanagement.dto.response.UserResponse;
import com.example.servicemanagement.enums.TicketPriority;
import com.example.servicemanagement.enums.TicketStatus;
import com.example.servicemanagement.service.TicketService;
import com.example.servicemanagement.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = ServiceManagementApplication.class)
@Transactional
class TicketControllerTest {

    @Autowired
    private UserService userService;

    @Autowired
    private TicketService ticketService;

    @Test
    void shouldCreateTicketWithAssignedUserAndOpenStatus() {
        UserResponse createdUser = userService.createUser(new CreateUserRequest("Alice", "alice@company.com"));

        TicketResponse ticket = ticketService.createTicket(new CreateTicketRequest(
                "VPN issue",
                "VPN access is failing for remote users",
                TicketPriority.HIGH,
                createdUser.id(),
                createdUser.id()
        ));

        assertThat(ticket.title()).isEqualTo("VPN issue");
        assertThat(ticket.status()).isEqualTo(TicketStatus.OPEN);
        assertThat(ticket.assignedUser()).isNotNull();
        assertThat(ticket.assignedUser().id()).isEqualTo(createdUser.id());
    }

    @Test
    void shouldRejectInvalidStatusTransition() {
        UserResponse createdUser = userService.createUser(new CreateUserRequest("Bob", "bob@company.com"));
        TicketResponse ticket = ticketService.createTicket(new CreateTicketRequest(
                "Infrastructure",
                "Database issue",
                TicketPriority.MEDIUM,
                createdUser.id(),
                createdUser.id()
        ));

        ticketService.updateStatus(ticket.id(), TicketStatus.RESOLVED);

        assertThatThrownBy(() -> ticketService.updateStatus(ticket.id(), TicketStatus.OPEN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot transition");
    }
}
