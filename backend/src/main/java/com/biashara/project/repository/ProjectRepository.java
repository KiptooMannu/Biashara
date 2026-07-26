package com.biashara.project.repository;

import com.biashara.common.enums.ProjectStatus;
import com.biashara.project.domain.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    @EntityGraph(attributePaths = {"client", "manager"})
    Page<Project> findByTenantIdAndDeletedFalse(Long tenantId, Pageable pageable);

    @EntityGraph(attributePaths = {"client", "manager"})
    List<Project> findByTenantIdAndDeletedFalseOrderByStartDateDesc(Long tenantId);

    Optional<Project> findByIdAndTenantIdAndDeletedFalse(Long id, Long tenantId);

    long countByTenantIdAndDeletedFalse(Long tenantId);

    long countByTenantIdAndStatusAndDeletedFalse(Long tenantId, ProjectStatus status);
}
