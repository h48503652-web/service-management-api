package com.example.servicemanagement.controller;

import com.example.servicemanagement.dto.request.CreateCommentRequest;
import com.example.servicemanagement.dto.request.CreateTicketRequest;
import com.example.servicemanagement.dto.request.UpdateTicketRequest;
import com.example.servicemanagement.dto.response.CommentResponse;
import com.example.servicemanagement.dto.response.TicketResponse;
import com.example.servicemanagement.enums.TicketPriority;
import com.example.servicemanagement.enums.TicketStatus;
import com.example.servicemanagement.service.TicketService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping("/tickets")
    public ResponseEntity<TicketResponse> createTicket(@Valid @RequestBody CreateTicketRequest request) {
        TicketResponse created = ticketService.createTicket(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/tickets")
    public Page<TicketResponse> getTickets(
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(required = false) TicketPriority priority,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {
        return ticketService.getTickets(status, priority, search, page, size, sortBy, direction);
    }

    @GetMapping("/tickets/{id}")
    public TicketResponse getTicketById(@PathVariable Long id) {
        return ticketService.getTicketById(id);
    }

    @PutMapping("/tickets/{id}")
    public TicketResponse updateTicket(@PathVariable Long id, @Valid @RequestBody UpdateTicketRequest request) {
        return ticketService.updateTicket(id, request);
    }

    @PatchMapping("/tickets/{id}/status")
    public TicketResponse updateStatus(@PathVariable Long id, @RequestParam TicketStatus status) {
        return ticketService.updateStatus(id, status);
    }

    @PatchMapping("/tickets/{id}/assignment")
    public TicketResponse assignUser(@PathVariable Long id, @RequestParam Long userId) {
        return ticketService.assignUser(id, userId);
    }

    @DeleteMapping("/tickets/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTicket(@PathVariable Long id) {
        ticketService.deleteTicket(id);
    }

    @PostMapping("/tickets/{id}/comments")
    public ResponseEntity<CommentResponse> addComment(@PathVariable Long id, @Valid @RequestBody CreateCommentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ticketService.addComment(id, request));
    }

    @GetMapping("/tickets/{id}/comments")
    public Page<CommentResponse> getComments(@PathVariable Long id,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "10") int size) {
        return ticketService.getComments(id, page, size);
    }
}
