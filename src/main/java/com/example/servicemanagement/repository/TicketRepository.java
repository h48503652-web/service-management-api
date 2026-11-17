package com.example.servicemanagement.repository;

import com.example.servicemanagement.entity.Ticket;
import com.example.servicemanagement.enums.TicketPriority;
import com.example.servicemanagement.enums.TicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    @Override
    @EntityGraph(attributePaths = {"createdBy", "assignedUser"})
    Optional<Ticket> findById(Long id);

    Page<Ticket> findByStatus(TicketStatus status, Pageable pageable);

    Page<Ticket> findByPriority(TicketPriority priority, Pageable pageable);

    @Query("select t from Ticket t where " +
            "(:status is null or t.status = :status) and " +
            "(:priority is null or t.priority = :priority) and " +
            "(:search is null or lower(t.title) like lower(concat('%', :search, '%')) or lower(t.description) like lower(concat('%', :search, '%'))) ")
    @EntityGraph(attributePaths = {"createdBy", "assignedUser"})
    Page<Ticket> searchTickets(@Param("status") TicketStatus status,
                               @Param("priority") TicketPriority priority,
                               @Param("search") String search,
                               Pageable pageable);
}
