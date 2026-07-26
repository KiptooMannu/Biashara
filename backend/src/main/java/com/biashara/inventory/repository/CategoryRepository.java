package com.biashara.inventory.repository;

import com.biashara.inventory.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findByTenantIdAndDeletedFalseOrderByNameAsc(Long tenantId);

    Optional<Category> findByTenantIdAndNameAndDeletedFalse(Long tenantId, String name);

    Optional<Category> findByIdAndTenantIdAndDeletedFalse(Long id, Long tenantId);

    long countByTenantIdAndDeletedFalse(Long tenantId);
}
