package com.biashara.sales.dto;

import com.biashara.sales.domain.Sale;
import com.biashara.sales.domain.SaleItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class SalesDtos {

    private SalesDtos() {
    }

    public record SaleItemResponse(
            Long id,
            Long productId,
            String productName,
            String sku,
            Integer quantity,
            BigDecimal unitPrice,
            BigDecimal discount,
            BigDecimal taxRate,
            BigDecimal taxAmount,
            BigDecimal lineTotal,
            BigDecimal lineProfit) {

        public static SaleItemResponse from(SaleItem item) {
            return new SaleItemResponse(
                    item.getId(),
                    item.getProduct() == null ? null : item.getProduct().getId(),
                    item.getProductName(),
                    item.getSku(),
                    item.getQuantity(),
                    item.getUnitPrice(),
                    item.getDiscount(),
                    item.getTaxRate(),
                    item.getTaxAmount(),
                    item.getLineTotal(),
                    item.getLineProfit());
        }
    }

    public record SaleResponse(
            Long id,
            String invoiceNumber,
            Long customerId,
            String customerName,
            String cashierName,
            String branchName,
            LocalDateTime saleDate,
            int itemCount,
            BigDecimal subtotal,
            BigDecimal taxAmount,
            BigDecimal discountAmount,
            BigDecimal total,
            BigDecimal grossProfit,
            String paymentMethod,
            String paymentStatus,
            String status,
            String channel,
            String paymentReference,
            List<SaleItemResponse> items) {

        /** Summary form for lists: no line items, so no lazy collection is touched. */
        public static SaleResponse summary(Sale sale) {
            return new SaleResponse(
                    sale.getId(),
                    sale.getInvoiceNumber(),
                    sale.getCustomer() == null ? null : sale.getCustomer().getId(),
                    sale.getCustomer() == null ? "Walk-in customer" : sale.getCustomer().getName(),
                    sale.getCashier() == null ? null : sale.getCashier().getFullName(),
                    sale.getBranch() == null ? null : sale.getBranch().getName(),
                    sale.getSaleDate(),
                    0,
                    sale.getSubtotal(),
                    sale.getTaxAmount(),
                    sale.getDiscountAmount(),
                    sale.getTotal(),
                    sale.getGrossProfit(),
                    sale.getPaymentMethod() == null ? null : sale.getPaymentMethod().name(),
                    sale.getPaymentStatus() == null ? null : sale.getPaymentStatus().name(),
                    sale.getStatus().name(),
                    sale.getChannel() == null ? null : sale.getChannel().name(),
                    sale.getPaymentReference(),
                    null);
        }

        /** Full form for a receipt view; requires the items to have been fetched. */
        public static SaleResponse detail(Sale sale) {
            List<SaleItemResponse> items = sale.getItems().stream()
                    .map(SaleItemResponse::from)
                    .toList();
            return new SaleResponse(
                    sale.getId(),
                    sale.getInvoiceNumber(),
                    sale.getCustomer() == null ? null : sale.getCustomer().getId(),
                    sale.getCustomer() == null ? "Walk-in customer" : sale.getCustomer().getName(),
                    sale.getCashier() == null ? null : sale.getCashier().getFullName(),
                    sale.getBranch() == null ? null : sale.getBranch().getName(),
                    sale.getSaleDate(),
                    items.size(),
                    sale.getSubtotal(),
                    sale.getTaxAmount(),
                    sale.getDiscountAmount(),
                    sale.getTotal(),
                    sale.getGrossProfit(),
                    sale.getPaymentMethod() == null ? null : sale.getPaymentMethod().name(),
                    sale.getPaymentStatus() == null ? null : sale.getPaymentStatus().name(),
                    sale.getStatus().name(),
                    sale.getChannel() == null ? null : sale.getChannel().name(),
                    sale.getPaymentReference(),
                    items);
        }
    }

    public record CartLine(
            @NotNull(message = "Product is required") Long productId,
            @NotNull @Min(value = 1, message = "Quantity must be at least 1") Integer quantity,
            @PositiveOrZero(message = "Discount cannot be negative") BigDecimal discount) {
    }

    /** A checkout from the point of sale. */
    public record CheckoutRequest(
            Long customerId,
            @NotEmpty(message = "Add at least one item to the cart")
            @Valid List<CartLine> lines,
            @NotNull(message = "Choose a payment method") String paymentMethod,
            BigDecimal amountPaid,
            String paymentReference,
            String notes) {
    }
}
