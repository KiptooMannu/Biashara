package com.biashara.sales.service;

import com.biashara.common.enums.InventoryTransactionType;
import com.biashara.common.enums.PaymentMethod;
import com.biashara.common.enums.PaymentStatus;
import com.biashara.common.enums.SaleStatus;
import com.biashara.common.enums.SalesChannel;
import com.biashara.common.exception.BusinessRuleException;
import com.biashara.common.exception.NotFoundException;
import com.biashara.crm.domain.Customer;
import com.biashara.crm.domain.CustomerInteraction;
import com.biashara.crm.repository.CustomerInteractionRepository;
import com.biashara.crm.repository.CustomerRepository;
import com.biashara.finance.domain.Payment;
import com.biashara.finance.repository.PaymentRepository;
import com.biashara.iam.domain.User;
import com.biashara.iam.repository.UserRepository;
import com.biashara.iam.service.AuditService;
import com.biashara.inventory.domain.InventoryTransaction;
import com.biashara.inventory.domain.Product;
import com.biashara.inventory.repository.InventoryTransactionRepository;
import com.biashara.inventory.repository.ProductRepository;
import com.biashara.sales.domain.Sale;
import com.biashara.sales.domain.SaleItem;
import com.biashara.sales.dto.SalesDtos;
import com.biashara.sales.repository.SaleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Records a sale at the till.
 *
 * The whole checkout is one transaction: stock is decremented, a movement is
 * written to the ledger, the payment is recorded, the customer's balance and
 * loyalty points are updated, and the timeline gets an entry. If any part fails —
 * insufficient stock, say — none of it happens, so stock can never drift from the
 * ledger.
 */
@Service
@RequiredArgsConstructor
public class PosService {

    private final SaleRepository saleRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final CustomerInteractionRepository interactionRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    @Transactional
    public SalesDtos.SaleResponse checkout(Long tenantId, Long cashierId, SalesDtos.CheckoutRequest request) {
        User cashier = userRepository.findByIdAndDeletedFalse(cashierId)
                .orElseThrow(() -> NotFoundException.of("User", cashierId));

        PaymentMethod method = parseMethod(request.paymentMethod());

        Customer customer = null;
        if (request.customerId() != null) {
            customer = customerRepository.findByIdAndTenantIdAndDeletedFalse(request.customerId(), tenantId)
                    .orElseThrow(() -> NotFoundException.of("Customer", request.customerId()));
        }

        if (method == PaymentMethod.CREDIT && customer == null) {
            throw new BusinessRuleException("A credit sale needs a customer account to bill");
        }

        Sale sale = Sale.builder()
                .tenant(cashier.getTenant())
                .invoiceNumber(nextInvoiceNumber(tenantId))
                .customer(customer)
                .cashier(cashier)
                .branch(cashier.getBranch())
                .saleDate(LocalDateTime.now())
                .status(SaleStatus.COMPLETED)
                .channel(SalesChannel.POS)
                .paymentMethod(method)
                .paymentReference(request.paymentReference())
                .notes(request.notes())
                .build();

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;
        BigDecimal discountTotal = BigDecimal.ZERO;
        List<Product> touched = new ArrayList<>();

        for (SalesDtos.CartLine line : request.lines()) {
            Product product = productRepository.findByIdAndTenantIdAndDeletedFalse(line.productId(), tenantId)
                    .orElseThrow(() -> NotFoundException.of("Product", line.productId()));

            if (!product.isActive()) {
                throw new BusinessRuleException(product.getName() + " is not available for sale");
            }
            if (product.getCurrentStock() < line.quantity()) {
                throw new BusinessRuleException(
                        "Only %d %s of %s left in stock".formatted(
                                product.getCurrentStock(), product.getUnit(), product.getName()));
            }

            BigDecimal lineDiscount = line.discount() == null ? BigDecimal.ZERO : line.discount();
            BigDecimal lineNet = product.getSellingPrice()
                    .multiply(BigDecimal.valueOf(line.quantity()))
                    .subtract(lineDiscount);

            if (lineNet.signum() < 0) {
                throw new BusinessRuleException(
                        "The discount on " + product.getName() + " is more than the line total");
            }

            BigDecimal vatRate = product.getVatRate() == null ? BigDecimal.ZERO : product.getVatRate();
            BigDecimal lineTax = lineNet.multiply(vatRate)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            sale.addItem(SaleItem.builder()
                    .product(product)
                    .productName(product.getName())
                    .sku(product.getSku())
                    .quantity(line.quantity())
                    .unitPrice(product.getSellingPrice())
                    // Cost snapshot: gross profit must stay correct after price changes.
                    .unitCost(product.getBuyingPrice())
                    .discount(lineDiscount)
                    .taxRate(vatRate)
                    .taxAmount(lineTax)
                    .lineTotal(lineNet.add(lineTax).setScale(2, RoundingMode.HALF_UP))
                    .returnedQuantity(0)
                    .build());

            subtotal = subtotal.add(lineNet);
            tax = tax.add(lineTax);
            cost = cost.add(product.getBuyingPrice().multiply(BigDecimal.valueOf(line.quantity())));
            discountTotal = discountTotal.add(lineDiscount);

            product.setCurrentStock(product.getCurrentStock() - line.quantity());
            touched.add(product);
        }

        BigDecimal total = subtotal.add(tax).setScale(2, RoundingMode.HALF_UP);
        boolean onCredit = method == PaymentMethod.CREDIT;
        BigDecimal tendered = request.amountPaid() == null ? total : request.amountPaid();

        if (!onCredit && tendered.compareTo(total) < 0) {
            throw new BusinessRuleException("Amount paid is less than the total due");
        }

        sale.setSubtotal(subtotal.setScale(2, RoundingMode.HALF_UP));
        sale.setTaxAmount(tax.setScale(2, RoundingMode.HALF_UP));
        sale.setDiscountAmount(discountTotal.setScale(2, RoundingMode.HALF_UP));
        sale.setTotal(total);
        sale.setCostOfGoods(cost.setScale(2, RoundingMode.HALF_UP));
        sale.setAmountPaid(onCredit ? BigDecimal.ZERO : total);
        sale.setChangeGiven(onCredit
                ? BigDecimal.ZERO
                : tendered.subtract(total).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
        sale.setPaymentStatus(onCredit ? PaymentStatus.UNPAID : PaymentStatus.PAID);

        Sale saved = saleRepository.save(sale);
        productRepository.saveAll(touched);

        // Stock ledger: one movement per line, carrying the resulting balance.
        List<InventoryTransaction> movements = new ArrayList<>();
        for (SaleItem item : saved.getItems()) {
            movements.add(InventoryTransaction.builder()
                    .tenant(saved.getTenant())
                    .product(item.getProduct())
                    .warehouse(item.getProduct().getWarehouse())
                    .type(InventoryTransactionType.STOCK_OUT)
                    .quantity(item.getQuantity())
                    .balanceAfter(item.getProduct().getCurrentStock())
                    .unitCost(item.getUnitCost())
                    .reference(saved.getInvoiceNumber())
                    .notes("Sold at the till")
                    .performedBy(cashier)
                    .occurredAt(saved.getSaleDate())
                    .build());
        }
        inventoryTransactionRepository.saveAll(movements);

        if (!onCredit) {
            paymentRepository.save(Payment.builder()
                    .tenant(saved.getTenant())
                    .paymentNumber("PAY-" + saved.getInvoiceNumber())
                    .sale(saved)
                    .customer(customer)
                    .amount(total)
                    .method(method)
                    .status(PaymentStatus.PAID)
                    .reference(request.paymentReference())
                    .paidAt(saved.getSaleDate())
                    .receivedBy(cashier)
                    .notes("Till payment for " + saved.getInvoiceNumber())
                    .build());
        }

        if (customer != null) {
            applyCustomerEffects(customer, saved, onCredit, total);
        }

        auditService.recordAs(cashier, saved.getTenant(), "RECORD_SALE", "Sales",
                "Sale", saved.getId(), saved.getInvoiceNumber(),
                "%d item(s), total %s, paid by %s".formatted(
                        saved.getItems().size(), total, method));

        return SalesDtos.SaleResponse.detail(saved);
    }

    /** Updates the customer's running totals, credit balance and loyalty points. */
    private void applyCustomerEffects(Customer customer, Sale sale, boolean onCredit, BigDecimal total) {
        BigDecimal spent = customer.getTotalSpent() == null ? BigDecimal.ZERO : customer.getTotalSpent();
        int orders = customer.getTotalOrders() == null ? 0 : customer.getTotalOrders();

        customer.setTotalSpent(spent.add(total));
        customer.setTotalOrders(orders + 1);
        customer.setAverageOrderValue(customer.getTotalSpent()
                .divide(BigDecimal.valueOf(orders + 1), 2, RoundingMode.HALF_UP));
        customer.setLastPurchaseAt(sale.getSaleDate());

        // A purchase resets recency, so churn risk drops immediately.
        customer.setRecencyScore(5);
        customer.setChurnRisk(BigDecimal.ZERO);

        if (onCredit) {
            BigDecimal outstanding = customer.getOutstandingBalance() == null
                    ? BigDecimal.ZERO : customer.getOutstandingBalance();
            BigDecimal updated = outstanding.add(total);

            if (customer.getCreditLimit() != null
                    && customer.getCreditLimit().signum() > 0
                    && updated.compareTo(customer.getCreditLimit()) > 0) {
                throw new BusinessRuleException(
                        "This sale would put %s over their credit limit of %s".formatted(
                                customer.getName(), customer.getCreditLimit()));
            }
            customer.setOutstandingBalance(updated);
        }

        // One loyalty point per 100 shillings spent.
        int earned = total.divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN).intValue();
        customer.setLoyaltyPoints((customer.getLoyaltyPoints() == null ? 0 : customer.getLoyaltyPoints()) + earned);

        customerRepository.save(customer);

        interactionRepository.save(CustomerInteraction.builder()
                .tenant(sale.getTenant())
                .customer(customer)
                .type(com.biashara.common.enums.InteractionType.PURCHASE)
                .subject("Purchase " + sale.getInvoiceNumber())
                .notes("%d item(s), %s. Earned %d loyalty point(s)."
                        .formatted(sale.getItems().size(), total, earned))
                .reference(sale.getInvoiceNumber())
                .handledBy(sale.getCashier())
                .outcome("Completed")
                .occurredAt(sale.getSaleDate())
                .build());
    }

    private PaymentMethod parseMethod(String raw) {
        try {
            return PaymentMethod.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException unknown) {
            throw new BusinessRuleException("Unknown payment method: " + raw);
        }
    }

    /**
     * Next invoice number for the tenant. Derived from the current count, which is
     * adequate for a single-node deployment; a production multi-node setup would
     * use a per-tenant database sequence to avoid a race.
     */
    private String nextInvoiceNumber(Long tenantId) {
        long count = saleRepository.countByTenantIdAndDeletedFalse(tenantId);
        String candidate = String.format("INV-%06d", count + 1);
        while (saleRepository.findByTenantIdAndInvoiceNumber(tenantId, candidate).isPresent()) {
            count++;
            candidate = String.format("INV-%06d", count + 1);
        }
        return candidate;
    }
}
