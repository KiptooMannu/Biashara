package com.biashara.inventory.service;

import com.biashara.common.enums.InventoryTransactionType;
import com.biashara.common.enums.ProductType;
import com.biashara.common.exception.BusinessRuleException;
import com.biashara.common.exception.NotFoundException;
import com.biashara.iam.domain.User;
import com.biashara.iam.repository.TenantRepository;
import com.biashara.iam.repository.UserRepository;
import com.biashara.iam.service.AuditService;
import com.biashara.inventory.domain.InventoryTransaction;
import com.biashara.inventory.domain.Product;
import com.biashara.inventory.dto.InventoryDtos;
import com.biashara.inventory.repository.CategoryRepository;
import com.biashara.inventory.repository.InventoryTransactionRepository;
import com.biashara.inventory.repository.ProductRepository;
import com.biashara.inventory.repository.WarehouseRepository;
import com.biashara.procurement.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Product maintenance and stock corrections.
 *
 * Any change to stock on hand is paired with a ledger movement in the same
 * transaction — the cached {@code currentStock} and the movement history are never
 * allowed to disagree.
 */
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final SupplierRepository supplierRepository;
    private final WarehouseRepository warehouseRepository;
    private final InventoryTransactionRepository movementRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    @Transactional
    public InventoryDtos.ProductResponse create(Long tenantId, Long actorId,
                                                InventoryDtos.ProductRequest request) {
        productRepository.findByTenantIdAndSkuAndDeletedFalse(tenantId, request.sku())
                .ifPresent(existing -> {
                    throw new BusinessRuleException("SKU " + request.sku() + " is already in use");
                });

        var tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> NotFoundException.of("Business", tenantId));

        Product product = Product.builder()
                .tenant(tenant)
                .sku(request.sku())
                .barcode(request.barcode())
                .name(request.name())
                .description(request.description())
                .productType(parseType(request.productType()))
                .unit(request.unit() == null ? "pc" : request.unit())
                .buyingPrice(request.buyingPrice())
                .sellingPrice(request.sellingPrice())
                .vatRate(request.vatRate())
                .currentStock(request.currentStock())
                .minStock(request.minStock())
                .maxStock(request.maxStock())
                .reorderLevel(request.reorderLevel())
                .expiryDate(request.expiryDate())
                .active(true)
                .salesVelocity(java.math.BigDecimal.ZERO)
                .build();

        applyRelations(product, tenantId, request);
        validatePricing(product);

        Product saved = productRepository.save(product);
        User actor = userRepository.findByIdAndDeletedFalse(actorId).orElse(null);

        // Opening stock is a real movement, so the ledger starts consistent.
        if (saved.getCurrentStock() != null && saved.getCurrentStock() > 0) {
            movementRepository.save(InventoryTransaction.builder()
                    .tenant(tenant)
                    .product(saved)
                    .warehouse(saved.getWarehouse())
                    .type(InventoryTransactionType.STOCK_IN)
                    .quantity(saved.getCurrentStock())
                    .balanceAfter(saved.getCurrentStock())
                    .unitCost(saved.getBuyingPrice())
                    .reference("OPENING")
                    .notes("Opening stock on product creation")
                    .performedBy(actor)
                    .occurredAt(LocalDateTime.now())
                    .build());
        }

        auditService.recordAs(actor, tenant, "CREATE_PRODUCT", "Inventory",
                "Product", saved.getId(), saved.getName(),
                "SKU %s, opening stock %d".formatted(saved.getSku(), saved.getCurrentStock()));

        return InventoryDtos.ProductResponse.from(saved);
    }

    @Transactional
    public InventoryDtos.ProductResponse update(Long tenantId, Long actorId, Long productId,
                                                InventoryDtos.ProductRequest request) {
        Product product = productRepository.findByIdAndTenantIdAndDeletedFalse(productId, tenantId)
                .orElseThrow(() -> NotFoundException.of("Product", productId));

        // A stock change through the edit form still has to hit the ledger.
        Integer previousStock = product.getCurrentStock();

        product.setSku(request.sku());
        product.setBarcode(request.barcode());
        product.setName(request.name());
        product.setDescription(request.description());
        product.setProductType(parseType(request.productType()));
        product.setUnit(request.unit() == null ? product.getUnit() : request.unit());
        product.setBuyingPrice(request.buyingPrice());
        product.setSellingPrice(request.sellingPrice());
        product.setVatRate(request.vatRate());
        product.setCurrentStock(request.currentStock());
        product.setMinStock(request.minStock());
        product.setMaxStock(request.maxStock());
        product.setReorderLevel(request.reorderLevel());
        product.setExpiryDate(request.expiryDate());

        applyRelations(product, tenantId, request);
        validatePricing(product);

        Product saved = productRepository.save(product);
        User actor = userRepository.findByIdAndDeletedFalse(actorId).orElse(null);

        if (previousStock != null && !previousStock.equals(saved.getCurrentStock())) {
            int delta = saved.getCurrentStock() - previousStock;
            movementRepository.save(InventoryTransaction.builder()
                    .tenant(saved.getTenant())
                    .product(saved)
                    .warehouse(saved.getWarehouse())
                    .type(InventoryTransactionType.ADJUSTMENT)
                    .quantity(Math.abs(delta))
                    .balanceAfter(saved.getCurrentStock())
                    .unitCost(saved.getBuyingPrice())
                    .reference("EDIT")
                    .notes("Stock corrected from %d to %d while editing the product"
                            .formatted(previousStock, saved.getCurrentStock()))
                    .performedBy(actor)
                    .occurredAt(LocalDateTime.now())
                    .build());
        }

        auditService.recordAs(actor, saved.getTenant(), "UPDATE_PRODUCT", "Inventory",
                "Product", saved.getId(), saved.getName(), "Product details updated");

        return InventoryDtos.ProductResponse.from(saved);
    }

    @Transactional
    public void softDelete(Long tenantId, Long actorId, Long productId) {
        Product product = productRepository.findByIdAndTenantIdAndDeletedFalse(productId, tenantId)
                .orElseThrow(() -> NotFoundException.of("Product", productId));

        product.setDeleted(true);
        product.setActive(false);
        productRepository.save(product);

        User actor = userRepository.findByIdAndDeletedFalse(actorId).orElse(null);
        auditService.recordAs(actor, product.getTenant(), "DELETE_PRODUCT", "Inventory",
                "Product", product.getId(), product.getName(),
                "Soft deleted; sales history is preserved");
    }

    /**
     * Applies a manual correction. Outbound types reduce stock and are refused if
     * they would drive it negative.
     */
    @Transactional
    public InventoryDtos.StockMovementResponse adjustStock(Long tenantId, Long actorId,
                                                           InventoryDtos.StockAdjustmentRequest request) {
        Product product = productRepository.findByIdAndTenantIdAndDeletedFalse(request.productId(), tenantId)
                .orElseThrow(() -> NotFoundException.of("Product", request.productId()));

        InventoryTransactionType type = parseMovementType(request.type());
        int signedQuantity = isInbound(type) ? request.quantity() : -request.quantity();
        int updated = product.getCurrentStock() + signedQuantity;

        if (updated < 0) {
            throw new BusinessRuleException(
                    "That would take %s to %d. Only %d in stock."
                            .formatted(product.getName(), updated, product.getCurrentStock()));
        }

        product.setCurrentStock(updated);
        productRepository.save(product);

        User actor = userRepository.findByIdAndDeletedFalse(actorId).orElse(null);

        InventoryTransaction movement = movementRepository.save(InventoryTransaction.builder()
                .tenant(product.getTenant())
                .product(product)
                .warehouse(product.getWarehouse())
                .type(type)
                .quantity(request.quantity())
                .balanceAfter(updated)
                .unitCost(product.getBuyingPrice())
                .reference("ADJUST")
                .notes(request.notes())
                .performedBy(actor)
                .occurredAt(LocalDateTime.now())
                .build());

        auditService.recordAs(actor, product.getTenant(), "ADJUST_STOCK", "Inventory",
                "Product", product.getId(), product.getName(),
                "%s %d %s. Balance now %d. %s".formatted(
                        type, request.quantity(), product.getUnit(), updated,
                        request.notes() == null ? "" : request.notes()));

        return InventoryDtos.StockMovementResponse.from(movement);
    }

    private void applyRelations(Product product, Long tenantId, InventoryDtos.ProductRequest request) {
        if (request.categoryId() != null) {
            product.setCategory(categoryRepository
                    .findByIdAndTenantIdAndDeletedFalse(request.categoryId(), tenantId)
                    .orElseThrow(() -> NotFoundException.of("Category", request.categoryId())));
        }
        if (request.supplierId() != null) {
            product.setSupplier(supplierRepository
                    .findByIdAndTenantIdAndDeletedFalse(request.supplierId(), tenantId)
                    .orElseThrow(() -> NotFoundException.of("Supplier", request.supplierId())));
        }
        if (request.warehouseId() != null) {
            warehouseRepository.findById(request.warehouseId()).ifPresent(product::setWarehouse);
        } else if (product.getWarehouse() == null) {
            warehouseRepository.findByTenantIdAndDefaultWarehouseTrueAndDeletedFalse(tenantId)
                    .ifPresent(product::setWarehouse);
        }
    }

    /** Selling below cost is almost always a data-entry error, so it is refused. */
    private void validatePricing(Product product) {
        if (product.getSellingPrice().compareTo(product.getBuyingPrice()) < 0) {
            throw new BusinessRuleException(
                    "Selling price is below the buying price — every sale would lose money");
        }
    }

    private ProductType parseType(String raw) {
        if (raw == null || raw.isBlank()) {
            return ProductType.PHYSICAL;
        }
        try {
            return ProductType.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException unknown) {
            throw new BusinessRuleException("Unknown product type: " + raw);
        }
    }

    private InventoryTransactionType parseMovementType(String raw) {
        try {
            return InventoryTransactionType.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException unknown) {
            throw new BusinessRuleException("Unknown movement type: " + raw);
        }
    }

    private boolean isInbound(InventoryTransactionType type) {
        return type == InventoryTransactionType.STOCK_IN
                || type == InventoryTransactionType.RETURN
                || type == InventoryTransactionType.TRANSFER_IN;
    }
}
