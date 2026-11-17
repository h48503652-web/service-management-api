package com.example.servicemanagement.dto.response;

import com.example.servicemanagement.entity.Ticket;
import com.example.servicemanagement.enums.TicketPriority;
import com.example.servicemanagement.enums.TicketStatus;

import java.time.LocalDateTime;

public record TicketResponse(
        Long id,
        String title,
        String description,
        TicketStatus status,
        TicketPriority priority,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        UserResponse assignedUser,
        UserResponse createdBy,
        int commentCount
) {
    public static TicketResponse from(Ticket ticket, long commentCount) {
        return new TicketResponse(
                ticket.getId(),
                ticket.getTitle(),
                ticket.getDescription(),
                ticket.getStatus(),
                ticket.getPriority(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt(),
                ticket.getAssignedUser() == null ? null : UserResponse.from(ticket.getAssignedUser()),
                UserResponse.from(ticket.getCreatedBy()),
                Math.toIntExact(commentCount)
        );
    }
}
