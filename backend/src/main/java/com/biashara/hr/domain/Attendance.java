package com.biashara.hr.domain;

import com.biashara.common.domain.TenantAwareEntity;
import com.biashara.common.enums.AttendanceStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "attendance", indexes = {
        @Index(name = "idx_attendance_tenant", columnList = "tenant_id"),
        @Index(name = "idx_attendance_date", columnList = "attendanceDate")
})
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Attendance extends TenantAwareEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(nullable = false)
    private LocalDate attendanceDate;

    private LocalTime checkIn;
    private LocalTime checkOut;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AttendanceStatus status;

    @Column(precision = 5, scale = 2)
    private BigDecimal hoursWorked;

    /** Minutes past the shift start; drives the punctuality metric. */
    private Integer minutesLate;

    @Column(precision = 5, scale = 2)
    private BigDecimal overtimeHours;

    @Column(length = 500)
    private String notes;
}
