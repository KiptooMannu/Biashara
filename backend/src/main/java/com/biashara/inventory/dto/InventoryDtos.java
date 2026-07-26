package com.biashara.inventory.dto;

import com.biashara.inventory.domain.Category;
import com.biashara.inventory.domain.InventoryTransaction;
import com.biashara.inventory.domain.Product;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class InventoryDtos {

    private InventoryDtos() {
    }

    public record ProductResponse(
            Long id,
            String sku,
            String barcode,
            String name,
            String description,
            String category,
            Long categoryId,
            String supplier,
            Long supplierId,
            String warehouse,
            String productType,
            String unit,
            BigDecimal buyingPrice,
            BigDecimal sellingPrice,
            BigDecimal vatRate,
            BigDecimal marginPercent,
            Integer currentStock,
            Integer minStock,
            Integer maxStock,
            Integer reorderLevel,
            BigDecimal stockValue,
            BigDecimal salesVelocity,
            BigDecimal daysUntilStockout,
            LocalDate expiryDate,
            boolean lowStock,
            boolean outOfStock,
            boolean active) {

        public static ProductResponse from(Product product) {
            return new ProductResponse(
                    product.getId(),
                    product.getSku(),
                    product.getBarcode(),
                    product.getName(),
                    product.getDescription(),
                    product.getCategory() == null ? null : product.getCategory().getName(),
                    product.getCategory() == null ? null : product.getCategory().getId(),
                    product.getSupplier() == null ? null : product.getSupplier().getName(),
                    product.getSupplier() == null ? null : product.getSupplier().getId(),
                    product.getWarehouse() == null ? null : product.getWarehouse().getName(),
                    product.getProductType() == null ? null : product.getProductType().name(),
                    product.getUnit(),
                    product.getBuyingPrice(),
                    product.getSellingPrice(),
                    product.getVatRate(),
                    product.getMarginPercent(),
                    product.getCurrentStock(),
                    product.getMinStock(),
                    product.getMaxStock(),
                    product.getReorderLevel(),
                    product.getStockValue(),
                    product.getSalesVelocity(),
                    product.getDaysUntilStockout(),
                    product.getExpiryDate(),
                    product.isLowStock(),
                    product.isOutOfStock(),
                    product.isActive());
        }
    }

    public record ProductRequest(
            @NotBlank(message = "SKU is required") String sku,
            String barcode,
            @NotBlank(message = "Product name is required") String name,
            String description,
            Long categoryId,
            Long supplierId,
            Long warehouseId,
            String productType,
            String unit,
            @NotNull(message = "Buying price is required")
            @PositiveOrZero(message = "Buying price cannot be negative") BigDecimal buyingPrice,
            @NotNull(message = "Selling price is required")
            @PositiveOrZero(message = "Selling price cannot be negative") BigDecimal sellingPrice,
            BigDecimal vatRate,
            @NotNull @Min(value = 0, message = "Stock cannot be negative") Integer currentStock,
            @NotNull @Min(value = 0, message = "Minimum stock cannot be negative") Integer minStock,
            Integer maxStock,
            Integer reorderLevel,
            LocalDate expiryDate) {
    }

    public record CategoryResponse(Long id, String name, String code, String colour, String description) {

        public static CategoryResponse from(Category category) {
            return new CategoryResponse(
                    category.getId(),
                    category.getName(),
                    category.getCode(),
                    category.getColour(),
                    category.getDescription());
        }
    }

    public record StockMovementResponse(
            Long id,
            Long productId,
            String productName,
            String sku,
            String warehouse,
            String type,
            Integer quantity,
            Integer balanceAfter,
            BigDecimal unitCost,
            String reference,
            String notes,
            String performedBy,
            boolean inbound,
            LocalDateTime occurredAt) {

        public static StockMovementResponse from(InventoryTransaction movement) {
            return new StockMovementResponse(
                    movement.getId(),
                    movement.getProduct().getId(),
                    movement.getProduct().getName(),
                    movement.getProduct().getSku(),
                    movement.getWarehouse() == null ? null : movement.getWarehouse().getName(),
                    movement.getType().name(),
                    movement.getQuantity(),
                    movement.getBalanceAfter(),
                    movement.getUnitCost(),
                    movement.getReference(),
                    movement.getNotes(),
                    movement.getPerformedBy() == null ? null : movement.getPerformedBy().getFullName(),
                    movement.isInbound(),
                    movement.getOccurredAt());
        }
    }

    /** A manual stock correction, recorded against a reason. */
    public record StockAdjustmentRequest(
            @NotNull(message = "Product is required") Long productId,
            @NotNull(message = "Movement type is required") String type,
            @NotNull @Min(value = 1, message = "Quantity must be at least 1") Integer quantity,
            String notes) {
    }
}
