package com.example.servicemanagement;

import com.example.servicemanagement.dto.request.CreateTicketRequest;
import com.example.servicemanagement.dto.request.CreateUserRequest;
import com.example.servicemanagement.dto.response.TicketResponse;
import com.example.servicemanagement.dto.response.UserResponse;
import com.example.servicemanagement.enums.TicketPriority;
import com.example.servicemanagement.enums.TicketStatus;
import com.example.servicemanagement.exception.DuplicateEmailException;
import com.example.servicemanagement.service.TicketService;
import com.example.servicemanagement.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class TicketServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private TicketService ticketService;

    @Test
    void shouldCreateTicketAndAssignToExistingUser() {
        UserResponse user = userService.createUser(new CreateUserRequest("Alice", "alice@company.com"));

        TicketResponse ticket = ticketService.createTicket(new CreateTicketRequest(
                "Security review",
                "Review firewall changes",
                TicketPriority.HIGH,
                user.id(),
                user.id()
        ));

        assertThat(ticket.title()).isEqualTo("Security review");
        assertThat(ticket.status()).isEqualTo(TicketStatus.OPEN);
        assertThat(ticket.assignedUser().id()).isEqualTo(user.id());
    }

    @Test
    void shouldRejectInvalidStatusTransition() {
        UserResponse user = userService.createUser(new CreateUserRequest("Bob", "bob@company.com"));
        TicketResponse ticket = ticketService.createTicket(new CreateTicketRequest(
                "Ticket",
                "Detailed investigation required before closing this issue.",
                TicketPriority.MEDIUM,
                user.id(),
                user.id()
        ));

        ticketService.updateStatus(ticket.id(), TicketStatus.RESOLVED);

        assertThatThrownBy(() -> ticketService.updateStatus(ticket.id(), TicketStatus.OPEN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot transition");
    }

    @Test
    void shouldPreventDuplicateEmail() {
        userService.createUser(new CreateUserRequest("Charlie", "charlie@company.com"));

        assertThatThrownBy(() -> userService.createUser(new CreateUserRequest("Charlie 2", "charlie@company.com")))
                .isInstanceOf(DuplicateEmailException.class);
    }
}
