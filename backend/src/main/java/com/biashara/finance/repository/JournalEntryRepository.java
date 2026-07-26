package com.biashara.finance.repository;

import com.biashara.finance.domain.JournalEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {

    @EntityGraph(attributePaths = {"createdBy"})
    Page<JournalEntry> findByTenantIdAndDeletedFalseOrderByEntryDateDesc(Long tenantId, Pageable pageable);

    @EntityGraph(attributePaths = {"lines", "lines.account", "createdBy"})
    Optional<JournalEntry> findByIdAndTenantIdAndDeletedFalse(Long id, Long tenantId);

    long countByTenantIdAndDeletedFalse(Long tenantId);
}
