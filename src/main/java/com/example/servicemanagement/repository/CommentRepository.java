package com.example.servicemanagement.repository;

import com.example.servicemanagement.entity.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {
    @EntityGraph(attributePaths = {"ticket", "author"})
    Page<Comment> findByTicketId(Long ticketId, Pageable pageable);

    long countByTicketId(Long ticketId);

    @Query("select c.ticket.id as ticketId, count(c) as commentCount " +
            "from Comment c where c.ticket.id in :ticketIds group by c.ticket.id")
    List<TicketCommentCount> countByTicketIds(@Param("ticketIds") Collection<Long> ticketIds);

    interface TicketCommentCount {
        Long getTicketId();
        Long getCommentCount();
    }
}
