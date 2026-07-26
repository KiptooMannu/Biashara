package com.biashara.project.repository;

import com.biashara.common.enums.TaskStatus;
import com.biashara.project.domain.ProjectTask;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectTaskRepository extends JpaRepository<ProjectTask, Long> {

    @EntityGraph(attributePaths = {"assignee", "project"})
    List<ProjectTask> findByTenantIdAndDeletedFalseOrderByBoardPositionAsc(Long tenantId);

    /** Tasks for one Kanban column. */
    @EntityGraph(attributePaths = {"assignee", "project"})
    List<ProjectTask> findByTenantIdAndStatusAndDeletedFalseOrderByBoardPositionAsc(
            Long tenantId, TaskStatus status);

    @EntityGraph(attributePaths = {"assignee", "project"})
    List<ProjectTask> findByTenantIdAndProjectIdAndDeletedFalse(Long tenantId, Long projectId);

    long countByTenantIdAndStatusAndDeletedFalse(Long tenantId, TaskStatus status);

    long countByTenantIdAndDeletedFalse(Long tenantId);
}
