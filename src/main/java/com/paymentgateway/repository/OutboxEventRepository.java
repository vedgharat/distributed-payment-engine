package com.paymentgateway.repository;

import com.paymentgateway.domain.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    // Relay job fetches up to 50 pending events per poll cycle.
    // Ordered by created_at so oldest events are retried first.
    @Query("""
        SELECT o FROM OutboxEvent o
        WHERE o.status = 'PENDING'
        ORDER BY o.createdAt ASC
        LIMIT 50
        """)
    List<OutboxEvent> findPendingEvents();
}