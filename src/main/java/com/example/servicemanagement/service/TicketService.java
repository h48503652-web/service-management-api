package com.example.servicemanagement.service;

import com.example.servicemanagement.dto.request.CreateCommentRequest;
import com.example.servicemanagement.dto.request.CreateTicketRequest;
import com.example.servicemanagement.dto.request.UpdateTicketRequest;
import com.example.servicemanagement.dto.response.CommentResponse;
import com.example.servicemanagement.dto.response.TicketResponse;
import com.example.servicemanagement.entity.Comment;
import com.example.servicemanagement.entity.Ticket;
import com.example.servicemanagement.entity.User;
import com.example.servicemanagement.enums.TicketPriority;
import com.example.servicemanagement.enums.TicketStatus;
import com.example.servicemanagement.exception.ResourceNotFoundException;
import com.example.servicemanagement.repository.CommentRepository;
import com.example.servicemanagement.repository.TicketRepository;
import com.example.servicemanagement.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Locale;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TicketService {

    private static final Logger log = LoggerFactory.getLogger(TicketService.class);
    private static final List<String> SORT_FIELDS = List.of("id", "title", "status", "priority", "createdAt", "updatedAt");

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final CommentRepository commentRepository;

    private static final Map<TicketStatus, java.util.Set<TicketStatus>> ALLOWED_TRANSITIONS = Map.of(
            TicketStatus.OPEN, java.util.Set.of(TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED, TicketStatus.CLOSED),
            TicketStatus.IN_PROGRESS, java.util.Set.of(TicketStatus.RESOLVED, TicketStatus.CLOSED),
            TicketStatus.RESOLVED, java.util.Set.of(TicketStatus.CLOSED),
            TicketStatus.CLOSED, java.util.Set.of()
    );

    public TicketService(TicketRepository ticketRepository, UserRepository userRepository, CommentRepository commentRepository) {
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.commentRepository = commentRepository;
    }

    @Transactional
    public TicketResponse createTicket(CreateTicketRequest request) {
        User createdBy = userRepository.findById(request.createdByUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", request.createdByUserId()));

        User assignedUser = request.assignedUserId() == null ? null : userRepository.findById(request.assignedUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", request.assignedUserId()));

        Ticket ticket = new Ticket(request.title().trim(), request.description().trim(), request.priority(), createdBy);
        ticket.setAssignedUser(assignedUser);
        ticket.setStatus(TicketStatus.OPEN);

        Ticket saved = ticketRepository.save(ticket);
        log.info("Created ticket {} for user {}", saved.getId(), createdBy.getId());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<TicketResponse> getTickets(TicketStatus status, TicketPriority priority, String search, int page, int size, String sortBy, String direction) {
        validatePagination(page, size);
        String field = sortBy == null ? "createdAt" : sortBy;
        if (!SORT_FIELDS.contains(field)) {
            throw new IllegalArgumentException("sortBy must be one of: " + String.join(", ", SORT_FIELDS));
        }
        String sortDirection = direction == null ? "DESC" : direction.toUpperCase(Locale.ROOT);
        if (!sortDirection.equals("ASC") && !sortDirection.equals("DESC")) {
            throw new IllegalArgumentException("direction must be ASC or DESC");
        }

        Sort sort = Sort.by(Sort.Direction.valueOf(sortDirection), field);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Ticket> tickets = ticketRepository.searchTickets(status, priority, search, pageable);
        if (tickets.isEmpty()) {
            return tickets.map(ticket -> TicketResponse.from(ticket, 0));
        }
        Map<Long, Long> commentCounts = commentRepository.countByTicketIds(
                        tickets.getContent().stream().map(Ticket::getId).toList())
                .stream()
                .collect(Collectors.toMap(
                        CommentRepository.TicketCommentCount::getTicketId,
                        CommentRepository.TicketCommentCount::getCommentCount));
        return tickets.map(ticket -> TicketResponse.from(ticket, commentCounts.getOrDefault(ticket.getId(), 0L)));
    }

    @Transactional(readOnly = true)
    public TicketResponse getTicketById(Long id) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", id));
        return toResponse(ticket);
    }

    @Transactional
    public TicketResponse updateTicket(Long id, UpdateTicketRequest request) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", id));

        ticket.setTitle(request.title().trim());
        ticket.setDescription(request.description().trim());
        ticket.setPriority(request.priority());

        if (request.assignedUserId() != null) {
            User assignedUser = userRepository.findById(request.assignedUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("User", request.assignedUserId()));
            ticket.setAssignedUser(assignedUser);
        } else {
            ticket.setAssignedUser(null);
        }

        // Flush before mapping so the response carries the updatedAt value Hibernate generates on update.
        return toResponse(ticketRepository.saveAndFlush(ticket));
    }

    @Transactional
    public TicketResponse updateStatus(Long ticketId, TicketStatus newStatus) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));

        TicketStatus currentStatus = ticket.getStatus();
        java.util.Set<TicketStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(currentStatus, java.util.Set.of());
        if (!allowed.contains(newStatus)) {
            throw new IllegalStateException("Ticket status '" + currentStatus + "' cannot transition to '" + newStatus + "'");
        }

        ticket.setStatus(newStatus);
        // Flush before mapping so the response carries the updatedAt value Hibernate generates on update.
        return toResponse(ticketRepository.saveAndFlush(ticket));
    }

    @Transactional
    public TicketResponse assignUser(Long ticketId, Long userId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        ticket.setAssignedUser(user);
        // Flush before mapping so the response carries the updatedAt value Hibernate generates on update.
        return toResponse(ticketRepository.saveAndFlush(ticket));
    }

    @Transactional
    public void deleteTicket(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));
        ticketRepository.delete(ticket);
    }

    @Transactional(readOnly = true)
    public Page<CommentResponse> getComments(Long ticketId, int page, int size) {
        validatePagination(page, size);
        if (!ticketRepository.existsById(ticketId)) {
            throw new ResourceNotFoundException("Ticket", ticketId);
        }
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return commentRepository.findByTicketId(ticketId, pageable).map(CommentResponse::from);
    }

    @Transactional
    public CommentResponse addComment(Long ticketId, CreateCommentRequest request) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));

        User author = userRepository.findByEmail(request.authorEmail().trim())
                .orElseThrow(() -> new ResourceNotFoundException("User", request.authorEmail()));

        Comment comment = new Comment(ticket, author, request.content().trim());
        Comment saved = commentRepository.save(comment);
        log.info("Added comment {} to ticket {} by user {}", saved.getId(), ticketId, author.getId());
        return CommentResponse.from(saved);
    }

    private TicketResponse toResponse(Ticket ticket) {
        return TicketResponse.from(ticket, commentRepository.countByTicketId(ticket.getId()));
    }

    private void validatePagination(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be greater than or equal to 0");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("size must be between 1 and 100");
        }
    }
}
