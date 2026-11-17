package com.example.servicemanagement.dto.response;

import com.example.servicemanagement.entity.Comment;

import java.time.LocalDateTime;

public record CommentResponse(Long id, Long ticketId, String authorEmail, String content, LocalDateTime createdAt) {
    public static CommentResponse from(Comment comment) {
        return new CommentResponse(
                comment.getId(),
                comment.getTicket().getId(),
                comment.getAuthor().getEmail(),
                comment.getContent(),
                comment.getCreatedAt()
        );
    }
}
