package com.biashara.hr.web;

import com.biashara.common.enums.ApprovalStatus;
import com.biashara.common.enums.AttendanceStatus;
import com.biashara.common.exception.BusinessRuleException;
import com.biashara.common.exception.NotFoundException;
import com.biashara.hr.dto.HrDtos;
import com.biashara.hr.repository.AttendanceRepository;
import com.biashara.hr.repository.EmployeeRepository;
import com.biashara.hr.repository.LeaveRequestRepository;
import com.biashara.hr.repository.PayrollRepository;
import com.biashara.iam.repository.UserRepository;
import com.biashara.iam.security.CurrentUser;
import com.biashara.iam.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/hr")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Human Resources", description = "Employees, attendance, leave and payroll")
public class HrController {

    private final EmployeeRepository employeeRepository;
    private final AttendanceRepository attendanceRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final PayrollRepository payrollRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final CurrentUser currentUser;

    @GetMapping("/employees")
    @PreAuthorize("hasAuthority('hr.employee.view')")
    @Operation(summary = "List employees")
    public Page<HrDtos.EmployeeResponse> employees(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return employeeRepository
                .findByTenantIdAndDeletedFalse(currentUser.tenantId(),
                        PageRequest.of(page, Math.min(size, 200), Sort.by("firstName").ascending()))
                .map(HrDtos.EmployeeResponse::from);
    }

    @GetMapping("/employees/{id}")
    @PreAuthorize("hasAuthority('hr.employee.view')")
    @Operation(summary = "One employee")
    public HrDtos.EmployeeResponse employee(@PathVariable Long id) {
        return employeeRepository.findByIdAndTenantIdAndDeletedFalse(id, currentUser.tenantId())
                .map(HrDtos.EmployeeResponse::from)
                .orElseThrow(() -> NotFoundException.of("Employee", id));
    }

    @GetMapping("/attendance")
    @PreAuthorize("hasAuthority('hr.attendance.view')")
    @Operation(summary = "Attendance records, newest first")
    public Page<HrDtos.AttendanceResponse> attendance(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return attendanceRepository
                .findByTenantIdAndDeletedFalseOrderByAttendanceDateDesc(currentUser.tenantId(),
                        PageRequest.of(page, Math.min(size, 200)))
                .map(HrDtos.AttendanceResponse::from);
    }

    @GetMapping("/attendance/today")
    @PreAuthorize("hasAuthority('hr.attendance.view')")
    @Operation(summary = "Today's attendance register")
    public List<HrDtos.AttendanceResponse> attendanceToday() {
        return attendanceRepository
                .findByTenantIdAndAttendanceDateAndDeletedFalse(currentUser.tenantId(), LocalDate.now())
                .stream()
                .map(HrDtos.AttendanceResponse::from)
                .toList();
    }

    @GetMapping("/leave")
    @PreAuthorize("hasAuthority('hr.leave.view')")
    @Operation(summary = "Leave requests")
    public Page<HrDtos.LeaveResponse> leave(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return leaveRequestRepository
                .findByTenantIdAndDeletedFalseOrderByStartDateDesc(currentUser.tenantId(),
                        PageRequest.of(page, Math.min(size, 200)))
                .map(HrDtos.LeaveResponse::from);
    }

    @GetMapping("/leave/pending")
    @PreAuthorize("hasAuthority('hr.leave.view')")
    @Operation(summary = "Leave requests awaiting a decision")
    public List<HrDtos.LeaveResponse> pendingLeave() {
        return leaveRequestRepository
                .findByTenantIdAndStatusAndDeletedFalseOrderByStartDateAsc(
                        currentUser.tenantId(), ApprovalStatus.PENDING)
                .stream()
                .map(HrDtos.LeaveResponse::from)
                .toList();
    }

    @PostMapping("/leave/{id}/decide")
    @PreAuthorize("hasAuthority('hr.leave.approve')")
    @Operation(summary = "Approve or reject a leave request")
    @Transactional
    public HrDtos.LeaveResponse decideLeave(@PathVariable Long id,
                                            @RequestParam boolean approve,
                                            @RequestParam(required = false) String notes) {
        var request = leaveRequestRepository.findById(id)
                .filter(candidate -> candidate.getTenant().getId().equals(currentUser.tenantId()))
                .orElseThrow(() -> NotFoundException.of("Leave request", id));

        if (request.getStatus() != ApprovalStatus.PENDING) {
            throw new BusinessRuleException("This request has already been decided");
        }

        var approver = userRepository.findByIdAndDeletedFalse(currentUser.userId()).orElse(null);
        request.setStatus(approve ? ApprovalStatus.APPROVED : ApprovalStatus.REJECTED);
        request.setApprovedBy(approver);
        request.setDecidedAt(java.time.LocalDateTime.now());
        request.setDecisionNotes(notes);

        // Approved leave is deducted from the balance straight away.
        if (approve) {
            var employee = request.getEmployee();
            var balance = employee.getLeaveBalance() == null
                    ? java.math.BigDecimal.ZERO : employee.getLeaveBalance();
            employee.setLeaveBalance(balance.subtract(request.getDays()).max(java.math.BigDecimal.ZERO));
            employeeRepository.save(employee);
        }

        var saved = leaveRequestRepository.save(request);

        auditService.recordAs(approver, saved.getTenant(),
                approve ? "APPROVE_LEAVE" : "REJECT_LEAVE", "HR",
                "LeaveRequest", saved.getId(), saved.getEmployee().getFullName(),
                "%s days of %s leave".formatted(saved.getDays(), saved.getLeaveType()));

        return HrDtos.LeaveResponse.from(saved);
    }

    @GetMapping("/payroll")
    @PreAuthorize("hasAuthority('hr.payroll.view')")
    @Operation(summary = "Payroll records")
    public Page<HrDtos.PayrollResponse> payroll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return payrollRepository
                .findByTenantIdAndDeletedFalseOrderByPeriodDesc(currentUser.tenantId(),
                        PageRequest.of(page, Math.min(size, 200)))
                .map(HrDtos.PayrollResponse::from);
    }

    @GetMapping("/payroll/{period}")
    @PreAuthorize("hasAuthority('hr.payroll.view')")
    @Operation(summary = "Payroll for one period, in YYYY-MM form")
    public List<HrDtos.PayrollResponse> payrollForPeriod(@PathVariable String period) {
        return payrollRepository.findByTenantIdAndPeriodAndDeletedFalse(currentUser.tenantId(), period)
                .stream()
                .map(HrDtos.PayrollResponse::from)
                .toList();
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('hr.employee.view')")
    @Operation(summary = "HR headline figures")
    public Map<String, Object> summary() {
        Long tenantId = currentUser.tenantId();
        LocalDate today = LocalDate.now();
        String period = String.format("%d-%02d", today.getYear(), today.getMonthValue());

        return Map.of(
                "totalEmployees", employeeRepository.countByTenantIdAndActiveTrueAndDeletedFalse(tenantId),
                "monthlyPayroll", employeeRepository.totalMonthlyPayroll(tenantId),
                "presentToday", attendanceRepository.countByTenantIdAndAttendanceDateAndStatusAndDeletedFalse(
                        tenantId, today, AttendanceStatus.PRESENT),
                "lateToday", attendanceRepository.countByTenantIdAndAttendanceDateAndStatusAndDeletedFalse(
                        tenantId, today, AttendanceStatus.LATE),
                "absentToday", attendanceRepository.countByTenantIdAndAttendanceDateAndStatusAndDeletedFalse(
                        tenantId, today, AttendanceStatus.ABSENT),
                "pendingLeave", leaveRequestRepository.countByTenantIdAndStatusAndDeletedFalse(
                        tenantId, ApprovalStatus.PENDING),
                "payrollThisPeriod", payrollRepository.sumNetPayForPeriod(tenantId, period),
                "headcountByDepartment", employeeRepository.headcountByDepartment(tenantId).stream()
                        .map(value -> Map.<String, Object>of(
                                "label", value.getLabel(),
                                "value", value.getValue(),
                                "count", value.getCount()))
                        .toList(),
                "attendanceMix", attendanceRepository.breakdownByStatus(tenantId,
                                today.minusDays(30), today).stream()
                        .map(value -> Map.<String, Object>of(
                                "label", value.getLabel(),
                                "value", value.getValue(),
                                "count", value.getCount()))
                        .toList(),
                "topPerformers", employeeRepository
                        .findTop5ByTenantIdAndDeletedFalseOrderByPerformanceScoreDesc(tenantId).stream()
                        .map(HrDtos.EmployeeResponse::from)
                        .toList());
    }
}
