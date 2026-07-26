package com.biashara.procurement.dto;

import com.biashara.procurement.domain.Purchase;
import com.biashara.procurement.domain.PurchaseItem;
import com.biashara.procurement.domain.Supplier;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class ProcurementDtos {

    private ProcurementDtos() {
    }

    public record SupplierResponse(
            Long id,
            String name,
            String code,
            String contactPerson,
            String phone,
            String email,
            String address,
            String city,
            Integer leadTimeDays,
            BigDecimal averageDeliveryDays,
            BigDecimal reliabilityScore,
            BigDecimal onTimeRate,
            Integer rating,
            Integer totalOrders,
            Integer lateDeliveries,
            BigDecimal totalPurchaseValue,
            BigDecimal outstandingBalance,
            String paymentTerms,
            boolean active) {

        public static SupplierResponse from(Supplier supplier) {
            return new SupplierResponse(
                    supplier.getId(),
                    supplier.getName(),
                    supplier.getCode(),
                    supplier.getContactPerson(),
                    supplier.getPhone(),
                    supplier.getEmail(),
                    supplier.getAddress(),
                    supplier.getCity(),
                    supplier.getLeadTimeDays(),
                    supplier.getAverageDeliveryDays(),
                    supplier.getReliabilityScore(),
                    supplier.getOnTimeRate(),
                    supplier.getRating(),
                    supplier.getTotalOrders(),
                    supplier.getLateDeliveries(),
                    supplier.getTotalPurchaseValue(),
                    supplier.getOutstandingBalance(),
                    supplier.getPaymentTerms(),
                    supplier.isActive());
        }
    }

    public record PurchaseItemResponse(
            Long id,
            Long productId,
            String productName,
            Integer quantity,
            Integer receivedQuantity,
            Integer outstandingQuantity,
            BigDecimal unitCost,
            BigDecimal lineTotal) {

        public static PurchaseItemResponse from(PurchaseItem item) {
            return new PurchaseItemResponse(
                    item.getId(),
                    item.getProduct() == null ? null : item.getProduct().getId(),
                    item.getProductName(),
                    item.getQuantity(),
                    item.getReceivedQuantity(),
                    item.getOutstandingQuantity(),
                    item.getUnitCost(),
                    item.getLineTotal());
        }
    }

    public record PurchaseResponse(
            Long id,
            String poNumber,
            Long supplierId,
            String supplierName,
            LocalDate orderDate,
            LocalDate expectedDelivery,
            LocalDate receivedDate,
            String status,
            String paymentStatus,
            BigDecimal subtotal,
            BigDecimal taxAmount,
            BigDecimal total,
            BigDecimal amountPaid,
            boolean overdue,
            String createdBy,
            String notes,
            List<PurchaseItemResponse> items) {

        public static PurchaseResponse summary(Purchase purchase) {
            return new PurchaseResponse(
                    purchase.getId(),
                    purchase.getPoNumber(),
                    purchase.getSupplier().getId(),
                    purchase.getSupplier().getName(),
                    purchase.getOrderDate(),
                    purchase.getExpectedDelivery(),
                    purchase.getReceivedDate(),
                    purchase.getStatus().name(),
                    purchase.getPaymentStatus() == null ? null : purchase.getPaymentStatus().name(),
                    purchase.getSubtotal(),
                    purchase.getTaxAmount(),
                    purchase.getTotal(),
                    purchase.getAmountPaid(),
                    purchase.isOverdue(),
                    purchase.getCreatedBy() == null ? null : purchase.getCreatedBy().getFullName(),
                    purchase.getNotes(),
                    null);
        }

        public static PurchaseResponse detail(Purchase purchase) {
            return new PurchaseResponse(
                    purchase.getId(),
                    purchase.getPoNumber(),
                    purchase.getSupplier().getId(),
                    purchase.getSupplier().getName(),
                    purchase.getOrderDate(),
                    purchase.getExpectedDelivery(),
                    purchase.getReceivedDate(),
                    purchase.getStatus().name(),
                    purchase.getPaymentStatus() == null ? null : purchase.getPaymentStatus().name(),
                    purchase.getSubtotal(),
                    purchase.getTaxAmount(),
                    purchase.getTotal(),
                    purchase.getAmountPaid(),
                    purchase.isOverdue(),
                    purchase.getCreatedBy() == null ? null : purchase.getCreatedBy().getFullName(),
                    purchase.getNotes(),
                    purchase.getItems().stream().map(PurchaseItemResponse::from).toList());
        }
    }

    /**
     * A reorder suggestion, computed from stock, velocity and supplier lead time
     * rather than a fixed threshold.
     */
    public record ReorderSuggestion(
            Long productId,
            String productName,
            String sku,
            Integer currentStock,
            Integer reorderLevel,
            BigDecimal salesVelocity,
            BigDecimal daysUntilStockout,
            Integer suggestedQuantity,
            BigDecimal estimatedCost,
            Long supplierId,
            String supplierName,
            Integer leadTimeDays,
            String urgency,
            String rationale) {
    }
}
