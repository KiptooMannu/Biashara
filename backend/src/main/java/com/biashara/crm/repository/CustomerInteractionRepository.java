package com.biashara.crm.repository;

import com.biashara.crm.domain.CustomerInteraction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomerInteractionRepository extends JpaRepository<CustomerInteraction, Long> {

    /** The customer timeline, newest first. */
    @EntityGraph(attributePaths = {"handledBy", "customer"})
    List<CustomerInteraction> findByTenantIdAndCustomerIdAndDeletedFalseOrderByOccurredAtDesc(
            Long tenantId, Long customerId);

    @EntityGraph(attributePaths = {"customer", "handledBy"})
    Page<CustomerInteraction> findByTenantIdAndDeletedFalseOrderByOccurredAtDesc(Long tenantId, Pageable pageable);

    long countByTenantIdAndDeletedFalse(Long tenantId);
}
