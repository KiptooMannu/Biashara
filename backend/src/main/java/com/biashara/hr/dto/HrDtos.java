package com.biashara.hr.dto;

import com.biashara.hr.domain.Attendance;
import com.biashara.hr.domain.Employee;
import com.biashara.hr.domain.LeaveRequest;
import com.biashara.hr.domain.Payroll;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

public final class HrDtos {

    private HrDtos() {
    }

    public record EmployeeResponse(
            Long id,
            String employeeNumber,
            String fullName,
            String email,
            String phone,
            String department,
            String branch,
            String position,
            String employmentType,
            LocalDate hireDate,
            BigDecimal basicSalary,
            BigDecimal allowances,
            BigDecimal grossSalary,
            BigDecimal performanceScore,
            BigDecimal leaveBalance,
            BigDecimal commissionRate,
            boolean hasSystemLogin,
            boolean active) {

        public static EmployeeResponse from(Employee employee) {
            return new EmployeeResponse(
                    employee.getId(),
                    employee.getEmployeeNumber(),
                    employee.getFullName(),
                    employee.getEmail(),
                    employee.getPhone(),
                    employee.getDepartment() == null ? null : employee.getDepartment().getName(),
                    employee.getBranch() == null ? null : employee.getBranch().getName(),
                    employee.getPosition(),
                    employee.getEmploymentType() == null ? null : employee.getEmploymentType().name(),
                    employee.getHireDate(),
                    employee.getBasicSalary(),
                    employee.getAllowances(),
                    employee.getGrossSalary(),
                    employee.getPerformanceScore(),
                    employee.getLeaveBalance(),
                    employee.getCommissionRate(),
                    employee.getUser() != null,
                    employee.isActive());
        }
    }

    public record AttendanceResponse(
            Long id,
            Long employeeId,
            String employeeName,
            LocalDate attendanceDate,
            LocalTime checkIn,
            LocalTime checkOut,
            String status,
            BigDecimal hoursWorked,
            Integer minutesLate,
            BigDecimal overtimeHours) {

        public static AttendanceResponse from(Attendance attendance) {
            return new AttendanceResponse(
                    attendance.getId(),
                    attendance.getEmployee().getId(),
                    attendance.getEmployee().getFullName(),
                    attendance.getAttendanceDate(),
                    attendance.getCheckIn(),
                    attendance.getCheckOut(),
                    attendance.getStatus().name(),
                    attendance.getHoursWorked(),
                    attendance.getMinutesLate(),
                    attendance.getOvertimeHours());
        }
    }

    public record LeaveResponse(
            Long id,
            Long employeeId,
            String employeeName,
            String leaveType,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal days,
            String reason,
            String status,
            String approvedBy,
            String decisionNotes) {

        public static LeaveResponse from(LeaveRequest request) {
            return new LeaveResponse(
                    request.getId(),
                    request.getEmployee().getId(),
                    request.getEmployee().getFullName(),
                    request.getLeaveType().name(),
                    request.getStartDate(),
                    request.getEndDate(),
                    request.getDays(),
                    request.getReason(),
                    request.getStatus().name(),
                    request.getApprovedBy() == null ? null : request.getApprovedBy().getFullName(),
                    request.getDecisionNotes());
        }
    }

    public record PayrollResponse(
            Long id,
            Long employeeId,
            String employeeName,
            String period,
            BigDecimal basicSalary,
            BigDecimal allowances,
            BigDecimal overtimePay,
            BigDecimal commission,
            BigDecimal grossPay,
            BigDecimal payeTax,
            BigDecimal nssfDeduction,
            BigDecimal nhifDeduction,
            BigDecimal totalDeductions,
            BigDecimal netPay,
            String status,
            LocalDate paidOn) {

        public static PayrollResponse from(Payroll payroll) {
            return new PayrollResponse(
                    payroll.getId(),
                    payroll.getEmployee().getId(),
                    payroll.getEmployee().getFullName(),
                    payroll.getPeriod(),
                    payroll.getBasicSalary(),
                    payroll.getAllowances(),
                    payroll.getOvertimePay(),
                    payroll.getCommission(),
                    payroll.getGrossPay(),
                    payroll.getPayeTax(),
                    payroll.getNssfDeduction(),
                    payroll.getNhifDeduction(),
                    payroll.getTotalDeductions(),
                    payroll.getNetPay(),
                    payroll.getStatus().name(),
                    payroll.getPaidOn());
        }
    }
}
