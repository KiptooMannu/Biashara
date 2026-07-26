package com.biashara.inventory.web;

import com.biashara.common.exception.NotFoundException;
import com.biashara.iam.security.CurrentUser;
import com.biashara.inventory.dto.InventoryDtos;
import com.biashara.inventory.repository.CategoryRepository;
import com.biashara.inventory.repository.InventoryTransactionRepository;
import com.biashara.inventory.repository.ProductRepository;
import com.biashara.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Inventory", description = "Products, stock levels, movements and predictions")
public class InventoryController {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final InventoryTransactionRepository movementRepository;
    private final InventoryService inventoryService;
    private final CurrentUser currentUser;

    @GetMapping("/products")
    @PreAuthorize("hasAuthority('inventory.product.view')")
    @Operation(summary = "List or search products")
    public Page<InventoryDtos.ProductResponse> products(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {

        Long tenantId = currentUser.tenantId();
        Pageable pageable = PageRequest.of(page, Math.min(size, 200), Sort.by("name").ascending());

        Page<com.biashara.inventory.domain.Product> found =
                search == null || search.isBlank()
                        ? productRepository.findByTenantIdAndDeletedFalse(tenantId, pageable)
                        : productRepository.search(tenantId, search.trim(), pageable);

        return found.map(InventoryDtos.ProductResponse::from);
    }

    @GetMapping("/products/{id}")
    @PreAuthorize("hasAuthority('inventory.product.view')")
    @Operation(summary = "One product")
    public InventoryDtos.ProductResponse product(@PathVariable Long id) {
        return productRepository.findByIdAndTenantIdAndDeletedFalse(id, currentUser.tenantId())
                .map(InventoryDtos.ProductResponse::from)
                .orElseThrow(() -> NotFoundException.of("Product", id));
    }

    /** Barcode scan, for the point of sale. */
    @GetMapping("/products/barcode/{barcode}")
    @PreAuthorize("hasAuthority('inventory.product.view')")
    @Operation(summary = "Look a product up by barcode")
    public InventoryDtos.ProductResponse byBarcode(@PathVariable String barcode) {
        return productRepository.findByTenantIdAndBarcodeAndDeletedFalse(currentUser.tenantId(), barcode)
                .map(InventoryDtos.ProductResponse::from)
                .orElseThrow(() -> new NotFoundException("No product with barcode " + barcode));
    }

    @PostMapping("/products")
    @PreAuthorize("hasAuthority('inventory.product.create')")
    @Operation(summary = "Add a product")
    public InventoryDtos.ProductResponse create(@Valid @RequestBody InventoryDtos.ProductRequest request) {
        return inventoryService.create(currentUser.tenantId(), currentUser.userId(), request);
    }

    @PutMapping("/products/{id}")
    @PreAuthorize("hasAuthority('inventory.product.update')")
    @Operation(summary = "Update a product")
    public InventoryDtos.ProductResponse update(@PathVariable Long id,
                                                @Valid @RequestBody InventoryDtos.ProductRequest request) {
        return inventoryService.update(currentUser.tenantId(), currentUser.userId(), id, request);
    }

    @DeleteMapping("/products/{id}")
    @PreAuthorize("hasAuthority('inventory.product.delete')")
    @Operation(summary = "Remove a product (soft delete)")
    public Map<String, Object> delete(@PathVariable Long id) {
        inventoryService.softDelete(currentUser.tenantId(), currentUser.userId(), id);
        return Map.of("success", true, "message", "Product removed");
    }

    @GetMapping("/categories")
    @PreAuthorize("hasAuthority('inventory.product.view')")
    @Operation(summary = "Product categories")
    public List<InventoryDtos.CategoryResponse> categories() {
        return categoryRepository.findByTenantIdAndDeletedFalseOrderByNameAsc(currentUser.tenantId()).stream()
                .map(InventoryDtos.CategoryResponse::from)
                .toList();
    }

    @GetMapping("/low-stock")
    @PreAuthorize("hasAuthority('inventory.product.view')")
    @Operation(summary = "Products at or below their minimum level")
    public List<InventoryDtos.ProductResponse> lowStock() {
        return productRepository.findLowStock(currentUser.tenantId()).stream()
                .map(InventoryDtos.ProductResponse::from)
                .toList();
    }

    @GetMapping("/stockout-risk")
    @PreAuthorize("hasAuthority('inventory.product.view')")
    @Operation(summary = "Products predicted to run out soonest, by sales velocity")
    public List<InventoryDtos.ProductResponse> stockoutRisk(@RequestParam(defaultValue = "10") int limit) {
        return productRepository.findByStockoutRisk(currentUser.tenantId(),
                        PageRequest.of(0, Math.min(limit, 50))).stream()
                .map(InventoryDtos.ProductResponse::from)
                .toList();
    }

    @GetMapping("/dead-stock")
    @PreAuthorize("hasAuthority('inventory.product.view')")
    @Operation(summary = "Stock with no movement, ranked by capital tied up")
    public List<InventoryDtos.ProductResponse> deadStock(@RequestParam(defaultValue = "10") int limit) {
        return productRepository.findDeadStock(currentUser.tenantId(),
                        PageRequest.of(0, Math.min(limit, 50))).stream()
                .map(InventoryDtos.ProductResponse::from)
                .toList();
    }

    @GetMapping("/expiring")
    @PreAuthorize("hasAuthority('inventory.product.view')")
    @Operation(summary = "Stock expiring within a number of days")
    public List<InventoryDtos.ProductResponse> expiring(@RequestParam(defaultValue = "30") int days) {
        return productRepository.findExpiringBetween(currentUser.tenantId(),
                        LocalDate.now(), LocalDate.now().plusDays(days)).stream()
                .map(InventoryDtos.ProductResponse::from)
                .toList();
    }

    @GetMapping("/movements")
    @PreAuthorize("hasAuthority('inventory.product.view')")
    @Operation(summary = "The stock movement ledger")
    public Page<InventoryDtos.StockMovementResponse> movements(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return movementRepository
                .findByTenantIdAndDeletedFalseOrderByOccurredAtDesc(
                        currentUser.tenantId(), PageRequest.of(page, Math.min(size, 200)))
                .map(InventoryDtos.StockMovementResponse::from);
    }

    @GetMapping("/products/{id}/movements")
    @PreAuthorize("hasAuthority('inventory.product.view')")
    @Operation(summary = "Movement history for one product")
    public List<InventoryDtos.StockMovementResponse> productMovements(@PathVariable Long id) {
        return movementRepository
                .findByTenantIdAndProductIdAndDeletedFalseOrderByOccurredAtDesc(currentUser.tenantId(), id)
                .stream()
                .map(InventoryDtos.StockMovementResponse::from)
                .toList();
    }

    @PostMapping("/adjust")
    @PreAuthorize("hasAuthority('inventory.stock.adjust')")
    @Operation(summary = "Adjust stock and record the movement")
    public InventoryDtos.StockMovementResponse adjust(
            @Valid @RequestBody InventoryDtos.StockAdjustmentRequest request) {
        return inventoryService.adjustStock(currentUser.tenantId(), currentUser.userId(), request);
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('inventory.product.view')")
    @Operation(summary = "Headline inventory figures")
    public Map<String, Object> summary() {
        Long tenantId = currentUser.tenantId();
        return Map.of(
                "totalProducts", productRepository.countByTenantIdAndDeletedFalse(tenantId),
                "stockValue", productRepository.totalStockValue(tenantId),
                "lowStockCount", productRepository.countLowStock(tenantId),
                "outOfStockCount", productRepository.countOutOfStock(tenantId),
                "stockValueByCategory", productRepository.stockValueByCategory(tenantId).stream()
                        .map(value -> Map.of(
                                "label", value.getLabel(),
                                "value", value.getValue(),
                                "count", value.getCount()))
                        .toList());
    }
}
