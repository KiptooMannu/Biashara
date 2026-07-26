package com.biashara.auth.service;

import com.biashara.auth.dto.AuthDtos;
import com.biashara.iam.domain.Role;
import com.biashara.iam.repository.RoleRepository;
import com.biashara.iam.repository.UserRepository;
import com.biashara.seed.SeedUsers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Exposes the seeded logins on the sign-in screen.
 *
 * Only accounts the seeder created are listed, and the list is derived from the
 * seed definitions rather than from a query for "all users with known passwords" —
 * so a real user created through the admin UI can never appear here.
 */
@Service
@RequiredArgsConstructor
public class DemoAccountService {

    /** Modules each role can reach, for the "can access" summary on the login card. */
    private static final List<String[]> MODULE_PROBES = List.of(
            new String[]{"Dashboard", "dashboard.view"},
            new String[]{"Business Intelligence", "analytics.view"},
            new String[]{"POS", "sales.pos.operate"},
            new String[]{"Sales", "sales.sale.view"},
            new String[]{"Inventory", "inventory.product.view"},
            new String[]{"CRM", "crm.customer.view"},
            new String[]{"Procurement", "procurement.supplier.view"},
            new String[]{"Finance", "finance.view"},
            new String[]{"HR", "hr.employee.view"},
            new String[]{"Assets", "asset.view"},
            new String[]{"Projects", "project.view"},
            new String[]{"Reports", "report.view"},
            new String[]{"AI Assistant", "ai.assistant.use"},
            new String[]{"User Management", "admin.user.view"},
            new String[]{"Audit Logs", "admin.audit.view"},
            new String[]{"Settings", "admin.settings.view"});

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    @Transactional(readOnly = true)
    public List<AuthDtos.DemoAccount> listDemoAccounts() {
        List<AuthDtos.DemoAccount> accounts = new ArrayList<>();

        for (SeedUsers.UserSeed seed : SeedUsers.all()) {
            // Skip anything the seeder did not actually create.
            if (userRepository.findByEmailIgnoreCaseAndDeletedFalse(seed.email()).isEmpty()) {
                continue;
            }

            Role role = roleRepository.findByCode(seed.roleCode()).orElse(null);
            Set<String> permissions = role == null
                    ? Set.of()
                    : role.getPermissions().stream()
                    .map(com.biashara.iam.domain.Permission::getCode)
                    .collect(java.util.stream.Collectors.toSet());

            List<String> canAccess = MODULE_PROBES.stream()
                    .filter(probe -> permissions.contains(probe[1]))
                    .map(probe -> probe[0])
                    .toList();

            accounts.add(new AuthDtos.DemoAccount(
                    seed.email(),
                    seed.password(),
                    seed.roleCode(),
                    role == null ? seed.roleCode() : role.getName(),
                    seed.firstName() + " " + seed.lastName(),
                    seed.position(),
                    seed.description(),
                    permissions.size(),
                    canAccess));
        }
        return accounts;
    }

    /** Headline accounts get prominence on the sign-in screen. */
    public Set<String> headlineEmails() {
        return SeedUsers.all().stream()
                .filter(SeedUsers.UserSeed::headline)
                .map(SeedUsers.UserSeed::email)
                .collect(java.util.stream.Collectors.toSet());
    }
}
