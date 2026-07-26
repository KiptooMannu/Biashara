package com.biashara.project.domain;

import com.biashara.common.domain.TenantAwareEntity;
import com.biashara.common.enums.Priority;
import com.biashara.common.enums.TaskStatus;
import com.biashara.iam.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Named ProjectTask rather than Task to avoid colliding with Spring's scheduling types. */
@Entity
@Table(name = "project_tasks")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class ProjectTask extends TenantAwareEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    @Column(nullable = false)
    private String title;

    @Column(length = 1000)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private User assignee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status;

    @Enumerated(EnumType.STRING)
    private Priority priority;

    private LocalDate dueDate;

    @Column(precision = 6, scale = 2)
    private BigDecimal estimatedHours;

    @Column(precision = 6, scale = 2)
    private BigDecimal actualHours;

    /** Ordering within a Kanban column. */
    private Integer boardPosition;

    public boolean isOverdue() {
        return dueDate != null && status != TaskStatus.DONE && dueDate.isBefore(LocalDate.now());
    }
}
