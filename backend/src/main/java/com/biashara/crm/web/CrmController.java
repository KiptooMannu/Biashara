package com.biashara.crm.web;

import com.biashara.common.enums.InteractionType;
import com.biashara.common.exception.BusinessRuleException;
import com.biashara.common.exception.NotFoundException;
import com.biashara.crm.domain.Customer;
import com.biashara.crm.domain.CustomerInteraction;
import com.biashara.crm.dto.CrmDtos;
import com.biashara.crm.repository.CustomerInteractionRepository;
import com.biashara.crm.repository.CustomerRepository;
import com.biashara.iam.repository.TenantRepository;
import com.biashara.iam.repository.UserRepository;
import com.biashara.iam.security.CurrentUser;
import com.biashara.iam.service.AuditService;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "CRM", description = "Customers, timelines, tiers and churn risk")
public class CrmController {

    private final CustomerRepository customerRepository;
    private final CustomerInteractionRepository interactionRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final CurrentUser currentUser;

    @GetMapping
    @PreAuthorize("hasAuthority('crm.customer.view')")
    @Operation(summary = "List or search customers")
    public Page<CrmDtos.CustomerResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {

        Long tenantId = currentUser.tenantId();
        Pageable pageable = PageRequest.of(page, Math.min(size, 200), Sort.by("name").ascending());

        Page<Customer> found = search == null || search.isBlank()
                ? customerRepository.findByTenantIdAndDeletedFalse(tenantId, pageable)
                : customerRepository.search(tenantId, search.trim(), pageable);

        return found.map(CrmDtos.CustomerResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('crm.customer.view')")
    @Operation(summary = "One customer")
    public CrmDtos.CustomerResponse detail(@PathVariable Long id) {
        return customerRepository.findByIdAndTenantIdAndDeletedFalse(id, currentUser.tenantId())
                .map(CrmDtos.CustomerResponse::from)
                .orElseThrow(() -> NotFoundException.of("Customer", id));
    }

    /** The complete relationship history: purchases, calls, visits, complaints. */
    @GetMapping("/{id}/timeline")
    @PreAuthorize("hasAuthority('crm.customer.view')")
    @Operation(summary = "Customer timeline")
    public List<CrmDtos.InteractionResponse> timeline(@PathVariable Long id) {
        return interactionRepository
                .findByTenantIdAndCustomerIdAndDeletedFalseOrderByOccurredAtDesc(currentUser.tenantId(), id)
                .stream()
                .map(CrmDtos.InteractionResponse::from)
                .toList();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('crm.customer.create')")
    @Operation(summary = "Add a customer")
    @Transactional
    public CrmDtos.CustomerResponse create(@Valid @RequestBody CrmDtos.CustomerRequest request) {
        Long tenantId = currentUser.tenantId();
        var tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> NotFoundException.of("Business", tenantId));

        Customer customer = Customer.builder()
                .tenant(tenant)
                .name(request.name())
                .phone(request.phone())
                .email(request.email())
                .address(request.address())
                .city(request.city())
                .customerType(request.customerType() == null ? "INDIVIDUAL" : request.customerType())
                .taxPin(request.taxPin())
                .creditLimit(request.creditLimit() == null ? BigDecimal.ZERO : request.creditLimit())
                .outstandingBalance(BigDecimal.ZERO)
                .totalSpent(BigDecimal.ZERO)
                .totalOrders(0)
                .averageOrderValue(BigDecimal.ZERO)
                .birthday(request.birthday())
                .notes(request.notes())
                // A new customer has no history, so they start unscored rather than
                // being assigned a tier that has not been earned.
                .tier(com.biashara.common.enums.CustomerTier.BRONZE)
                .churnRisk(BigDecimal.ZERO)
                .lifetimeValue(BigDecimal.ZERO)
                .active(true)
                .build();

        Customer saved = customerRepository.save(customer);

        auditService.recordAs(
                userRepository.findByIdAndDeletedFalse(currentUser.userId()).orElse(null),
                tenant, "CREATE_CUSTOMER", "CRM", "Customer", saved.getId(), saved.getName(),
                "Customer account created");

        return CrmDtos.CustomerResponse.from(saved);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('crm.customer.update')")
    @Operation(summary = "Update a customer")
    @Transactional
    public CrmDtos.CustomerResponse update(@PathVariable Long id,
                                           @Valid @RequestBody CrmDtos.CustomerRequest request) {
        Customer customer = customerRepository
                .findByIdAndTenantIdAndDeletedFalse(id, currentUser.tenantId())
                .orElseThrow(() -> NotFoundException.of("Customer", id));

        customer.setName(request.name());
        customer.setPhone(request.phone());
        customer.setEmail(request.email());
        customer.setAddress(request.address());
        customer.setCity(request.city());
        if (request.customerType() != null) {
            customer.setCustomerType(request.customerType());
        }
        customer.setTaxPin(request.taxPin());
        if (request.creditLimit() != null) {
            customer.setCreditLimit(request.creditLimit());
        }
        customer.setBirthday(request.birthday());
        customer.setNotes(request.notes());

        Customer saved = customerRepository.save(customer);

        auditService.recordAs(
                userRepository.findByIdAndDeletedFalse(currentUser.userId()).orElse(null),
                saved.getTenant(), "UPDATE_CUSTOMER", "CRM", "Customer", saved.getId(),
                saved.getName(), "Customer details updated");

        return CrmDtos.CustomerResponse.from(saved);
    }

    @PostMapping("/{id}/interactions")
    @PreAuthorize("hasAuthority('crm.customer.update')")
    @Operation(summary = "Log an interaction on the timeline")
    @Transactional
    public CrmDtos.InteractionResponse logInteraction(
            @PathVariable Long id,
            @Valid @RequestBody CrmDtos.InteractionRequest request) {

        Customer customer = customerRepository
                .findByIdAndTenantIdAndDeletedFalse(id, currentUser.tenantId())
                .orElseThrow(() -> NotFoundException.of("Customer", id));

        InteractionType type;
        try {
            type = InteractionType.valueOf(request.type().toUpperCase());
        } catch (IllegalArgumentException unknown) {
            throw new BusinessRuleException("Unknown interaction type: " + request.type());
        }

        CustomerInteraction saved = interactionRepository.save(CustomerInteraction.builder()
                .tenant(customer.getTenant())
                .customer(customer)
                .type(type)
                .subject(request.subject())
                .notes(request.notes())
                .outcome(request.outcome())
                .handledBy(userRepository.findByIdAndDeletedFalse(currentUser.userId()).orElse(null))
                .occurredAt(LocalDateTime.now())
                .build());

        return CrmDtos.InteractionResponse.from(saved);
    }

    @GetMapping("/at-risk")
    @PreAuthorize("hasAuthority('crm.customer.view')")
    @Operation(summary = "Customers most likely to churn — the retention call list")
    public List<CrmDtos.CustomerResponse> atRisk(@RequestParam(defaultValue = "60") int threshold,
                                                 @RequestParam(defaultValue = "15") int limit) {
        return customerRepository.findAtRisk(currentUser.tenantId(),
                        BigDecimal.valueOf(threshold), PageRequest.of(0, Math.min(limit, 100)))
                .stream()
                .map(CrmDtos.CustomerResponse::from)
                .toList();
    }

    @GetMapping("/owing")
    @PreAuthorize("hasAuthority('crm.customer.view')")
    @Operation(summary = "Customers carrying an outstanding balance")
    public List<CrmDtos.CustomerResponse> owing() {
        return customerRepository.findWithOutstandingBalance(currentUser.tenantId()).stream()
                .map(CrmDtos.CustomerResponse::from)
                .toList();
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('crm.customer.view')")
    @Operation(summary = "CRM headline figures and tier distribution")
    public Map<String, Object> summary() {
        Long tenantId = currentUser.tenantId();
        return Map.of(
                "totalCustomers", customerRepository.countByTenantIdAndDeletedFalse(tenantId),
                "totalReceivables", customerRepository.totalReceivables(tenantId),
                "byTier", customerRepository.countByTier(tenantId).stream()
                        .map(value -> Map.of(
                                "label", value.getLabel() == null ? "Unscored" : value.getLabel(),
                                "value", value.getValue(),
                                "count", value.getCount()))
                        .toList(),
                "topCustomers", customerRepository
                        .findTop10ByTenantIdAndDeletedFalseOrderByTotalSpentDesc(tenantId).stream()
                        .map(CrmDtos.CustomerResponse::from)
                        .toList());
    }
}
