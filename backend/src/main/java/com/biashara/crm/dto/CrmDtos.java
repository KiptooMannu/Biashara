package com.biashara.crm.dto;

import com.biashara.crm.domain.Customer;
import com.biashara.crm.domain.CustomerInteraction;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class CrmDtos {

    private CrmDtos() {
    }

    public record CustomerResponse(
            Long id,
            String name,
            String phone,
            String email,
            String address,
            String city,
            String customerType,
            String taxPin,
            String tier,
            Integer loyaltyPoints,
            BigDecimal creditLimit,
            BigDecimal outstandingBalance,
            BigDecimal totalSpent,
            Integer totalOrders,
            BigDecimal averageOrderValue,
            LocalDateTime lastPurchaseAt,
            LocalDate birthday,
            BigDecimal churnRisk,
            BigDecimal lifetimeValue,
            String rfmSegment,
            boolean overCreditLimit,
            boolean active) {

        public static CustomerResponse from(Customer customer) {
            return new CustomerResponse(
                    customer.getId(),
                    customer.getName(),
                    customer.getPhone(),
                    customer.getEmail(),
                    customer.getAddress(),
                    customer.getCity(),
                    customer.getCustomerType(),
                    customer.getTaxPin(),
                    customer.getTier() == null ? null : customer.getTier().name(),
                    customer.getLoyaltyPoints(),
                    customer.getCreditLimit(),
                    customer.getOutstandingBalance(),
                    customer.getTotalSpent(),
                    customer.getTotalOrders(),
                    customer.getAverageOrderValue(),
                    customer.getLastPurchaseAt(),
                    customer.getBirthday(),
                    customer.getChurnRisk(),
                    customer.getLifetimeValue(),
                    customer.getRfmSegment(),
                    customer.isOverCreditLimit(),
                    customer.isActive());
        }
    }

    public record CustomerRequest(
            @NotBlank(message = "Customer name is required") String name,
            String phone,
            String email,
            String address,
            String city,
            String customerType,
            String taxPin,
            BigDecimal creditLimit,
            LocalDate birthday,
            String notes) {
    }

    public record InteractionResponse(
            Long id,
            Long customerId,
            String customerName,
            String type,
            String subject,
            String notes,
            String reference,
            String handledBy,
            String outcome,
            LocalDateTime occurredAt) {

        public static InteractionResponse from(CustomerInteraction interaction) {
            return new InteractionResponse(
                    interaction.getId(),
                    interaction.getCustomer().getId(),
                    interaction.getCustomer().getName(),
                    interaction.getType().name(),
                    interaction.getSubject(),
                    interaction.getNotes(),
                    interaction.getReference(),
                    interaction.getHandledBy() == null ? null : interaction.getHandledBy().getFullName(),
                    interaction.getOutcome(),
                    interaction.getOccurredAt());
        }
    }

    public record InteractionRequest(
            @NotBlank String type,
            @NotBlank String subject,
            String notes,
            String outcome) {
    }
}
