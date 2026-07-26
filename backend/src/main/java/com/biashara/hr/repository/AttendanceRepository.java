package com.biashara.hr.repository;

import com.biashara.analytics.projection.DailySeriesPoint;
import com.biashara.analytics.projection.LabelledValue;
import com.biashara.common.enums.AttendanceStatus;
import com.biashara.hr.domain.Attendance;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    @EntityGraph(attributePaths = {"employee"})
    Page<Attendance> findByTenantIdAndDeletedFalseOrderByAttendanceDateDesc(Long tenantId, Pageable pageable);

    @EntityGraph(attributePaths = {"employee"})
    List<Attendance> findByTenantIdAndAttendanceDateAndDeletedFalse(Long tenantId, LocalDate date);

    List<Attendance> findByTenantIdAndEmployeeIdAndDeletedFalseOrderByAttendanceDateDesc(
            Long tenantId, Long employeeId);

    long countByTenantIdAndDeletedFalse(Long tenantId);

    long countByTenantIdAndAttendanceDateAndStatusAndDeletedFalse(
            Long tenantId, LocalDate date, AttendanceStatus status);

    /** Attendance mix over a window, for the HR dashboard donut. */
    @Query("""
            select cast(a.status as String) as label, count(a) as value, count(a) as count
            from Attendance a
            where a.tenant.id = :tenantId and a.deleted = false
              and a.attendanceDate between :from and :to
            group by a.status
            """)
    List<LabelledValue> breakdownByStatus(@Param("tenantId") Long tenantId,
                                          @Param("from") LocalDate from,
                                          @Param("to") LocalDate to);

    /** Daily present-versus-absent counts, for the attendance trend. */
    @Query(value = """
            select to_char(a.attendance_date, 'YYYY-MM-DD') as bucket,
                   sum(case when a.status in ('PRESENT', 'LATE', 'HALF_DAY') then 1 else 0 end) as value,
                   sum(case when a.status = 'ABSENT' then 1 else 0 end) as secondary,
                   count(*) as count
            from attendance a
            where a.tenant_id = :tenantId and a.deleted = false and a.attendance_date >= :from
            group by to_char(a.attendance_date, 'YYYY-MM-DD')
            order by bucket asc
            """, nativeQuery = true)
    List<DailySeriesPoint> dailyAttendanceSeries(@Param("tenantId") Long tenantId,
                                                 @Param("from") LocalDate from);
}
