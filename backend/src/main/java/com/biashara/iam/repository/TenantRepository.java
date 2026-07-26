package com.biashara.iam.repository;

import com.biashara.iam.domain.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TenantRepository extends JpaRepository<Tenant, Long> {

    Optional<Tenant> findBySlug(String slug);

    List<Tenant> findByDeletedFalse();

    boolean existsBySlug(String slug);
}
