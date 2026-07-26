package com.biashara.seed;

import com.biashara.common.enums.ApprovalStatus;
import com.biashara.common.enums.TenantStatus;
import com.biashara.common.enums.UserStatus;
import com.biashara.iam.domain.Branch;
import com.biashara.iam.domain.Department;
import com.biashara.iam.domain.Permission;
import com.biashara.iam.domain.Role;
import com.biashara.iam.domain.Tenant;
import com.biashara.iam.domain.User;
import com.biashara.iam.domain.UserInvitation;
import com.biashara.iam.repository.BranchRepository;
import com.biashara.iam.repository.DepartmentRepository;
import com.biashara.iam.repository.PermissionRepository;
import com.biashara.iam.repository.RoleRepository;
import com.biashara.iam.repository.TenantRepository;
import com.biashara.iam.repository.UserInvitationRepository;
import com.biashara.iam.repository.UserRepository;
import com.biashara.iam.security.Permissions;
import com.biashara.iam.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Stages 1-3: permissions, roles, the tenant, its structure and its users. */
@Component
@RequiredArgsConstructor
public class IamSeeder {

    private static final Logger log = LoggerFactory.getLogger(IamSeeder.class);

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final TenantRepository tenantRepository;
    private final DepartmentRepository departmentRepository;
    private final BranchRepository branchRepository;
    private final UserRepository userRepository;
    private final UserInvitationRepository invitationRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    /** Handles the rest of the seed needs. */
    public record IamContext(
            Tenant tenant,
            Branch mainBranch,
            List<Branch> branches,
            Map<String, Department> departments,
            Map<String, Role> roles,
            User owner,
            User generalManager,
            User cashier,
            User salesManager,
            User financeManager,
            User hrManager,
            User inventoryManager,
            User storekeeper,
            List<User> allUsers,
            /** Users who can appear as a cashier on a sale. */
            List<User> tillOperators) {
    }

    @Transactional
    public IamContext seed(String slug) {
        Map<String, Permission> permissions = seedPermissions();
        Tenant tenant = seedTenant(slug);
        List<Branch> branches = seedBranches(tenant);
        Map<String, Department> departments = seedDepartments(tenant);
        Map<String, Role> roles = seedRoles(tenant, permissions, departments);
        List<User> users = seedUsers(tenant, roles, departments, branches);

        Map<String, User> byRole = new HashMap<>();
        users.forEach(user -> user.getRoles().forEach(role -> byRole.putIfAbsent(role.getCode(), user)));

        User owner = byRole.get("BUSINESS_OWNER");
        setDepartmentHeads(departments, byRole);

        // Anyone who can operate the till is a candidate cashier on seeded sales.
        List<User> tillOperators = users.stream()
                .filter(user -> user.collectPermissionCodes().contains(Permissions.POS_OPERATE))
                .toList();

        seedInvitations(tenant, users, owner);

        return new IamContext(
                tenant,
                branches.get(0),
                branches,
                departments,
                roles,
                owner,
                byRole.get("GENERAL_MANAGER"),
                byRole.get("CASHIER"),
                byRole.get("SALES_MANAGER"),
                byRole.get("FINANCE_MANAGER"),
                byRole.get("HR_MANAGER"),
                byRole.get("INVENTORY_MANAGER"),
                byRole.get("STOREKEEPER"),
                users,
                tillOperators.isEmpty() ? users : tillOperators);
    }

    private Map<String, Permission> seedPermissions() {
        Map<String, Permission> saved = new HashMap<>();
        List<Permission> batch = new ArrayList<>();
        for (String[] entry : Permissions.catalogue()) {
            batch.add(Permission.builder()
                    .code(entry[0])
                    .module(entry[1])
                    .description(entry[2])
                    .build());
        }
        permissionRepository.saveAll(batch).forEach(permission -> saved.put(permission.getCode(), permission));
        log.info("Seeded {} permissions", saved.size());
        return saved;
    }

    private Tenant seedTenant(String slug) {
        Tenant tenant = tenantRepository.save(Tenant.builder()
                .name("GreenMart Supermarket")
                .slug(slug)
                .businessType("Retail")
                .industry("Retail — Supermarket & Grocery")
                .location("Ngong Road, Nairobi")
                .city("Nairobi")
                .country("Kenya")
                .currency("KES")
                .timezone("Africa/Nairobi")
                .phone("+254 20 200 3000")
                .email("info@greenmart.co.ke")
                .website("https://greenmart.co.ke")
                .taxPin("P051234567M")
                .defaultVatRate(new BigDecimal("16.00"))
                .status(TenantStatus.ACTIVE)
                .subscriptionPlan("BUSINESS")
                .subscriptionStartedAt(LocalDate.now().minusMonths(8))
                .maxUsers(50)
                .maxProducts(5000)
                // Target set close to actual trading volume so goal progress is a
                // real measure rather than a number nobody could ever hit.
                .monthlyRevenueTarget(new BigDecimal("2500000"))
                .build());
        log.info("Seeded tenant {}", tenant.getName());
        return tenant;
    }

    /**
     * A ten-branch chain. Branch-comparison reporting is a headline feature and
     * needs enough locations for the comparison to say something; three made for a
     * thin chart.
     */
    private List<Branch> seedBranches(Tenant tenant) {
        String[][] definitions = {
                {"Ngong Road", "Nairobi"},
                {"Westlands", "Nairobi"},
                {"Thika Road Mall", "Nairobi"},
                {"Karen", "Nairobi"},
                {"Embakasi", "Nairobi"},
                {"Kasarani", "Nairobi"},
                {"Nakuru Town", "Nakuru"},
                {"Mombasa Nyali", "Mombasa"},
                {"Kisumu Central", "Kisumu"},
                {"Eldoret Town", "Eldoret"}};

        List<Branch> batch = new ArrayList<>();
        int index = 1;
        for (String[] definition : definitions) {
            batch.add(Branch.builder()
                    .tenant(tenant)
                    .name("GreenMart " + definition[0])
                    .code(String.format("BR-%03d", index))
                    .location(definition[0])
                    .city(definition[1])
                    .phone("+254 20 200 " + (3000 + index))
                    // The first is the flagship store.
                    .mainBranch(index == 1)
                    .build());
            index++;
        }

        List<Branch> branches = branchRepository.saveAll(batch);
        log.info("Seeded {} branches", branches.size());
        return branches;
    }

    private Map<String, Department> seedDepartments(Tenant tenant) {
        List<Department> departments = departmentRepository.saveAll(List.of(
                dept(tenant, "Executive", "EXE", "Ownership and strategy", "0"),
                dept(tenant, "Finance", "FIN", "Accounting, payables and receivables", "350000"),
                dept(tenant, "Human Resources", "HR", "Staffing, payroll and welfare", "180000"),
                dept(tenant, "Operations", "OPS", "Inventory, warehousing and procurement", "420000"),
                dept(tenant, "Sales", "SLS", "Point of sale and customer accounts", "300000"),
                dept(tenant, "Customer Service", "CS", "Support and complaints", "120000"),
                dept(tenant, "IT", "IT", "Systems and support", "150000"),
                dept(tenant, "Security", "SEC", "Premises and loss prevention", "90000"),
                dept(tenant, "Maintenance", "MNT", "Facilities and equipment", "80000"),
                dept(tenant, "Marketing", "MKT", "Promotions and campaigns", "200000")));

        Map<String, Department> byCode = new HashMap<>();
        departments.forEach(department -> byCode.put(department.getCode(), department));
        log.info("Seeded {} departments", departments.size());
        return byCode;
    }

    private Department dept(Tenant tenant, String name, String code, String description, String budget) {
        return Department.builder()
                .tenant(tenant)
                .name(name)
                .code(code)
                .description(description)
                .monthlyBudget(new BigDecimal(budget))
                .build();
    }

    private Map<String, Role> seedRoles(Tenant tenant,
                                        Map<String, Permission> permissions,
                                        Map<String, Department> departments) {
        Map<String, Role> byCode = new HashMap<>();

        for (SeedRoles.RoleSeed seed : SeedRoles.all()) {
            Set<Permission> granted = new HashSet<>();
            for (String code : seed.permissions()) {
                Permission permission = permissions.get(code);
                if (permission == null) {
                    // A typo in the matrix would otherwise silently grant nothing.
                    throw new IllegalStateException(
                            "Role " + seed.code() + " references unknown permission " + code);
                }
                granted.add(permission);
            }

            Role role = Role.builder()
                    .code(seed.code())
                    .name(seed.name())
                    .description(seed.description())
                    .hierarchyLevel(seed.level())
                    // SUPER_ADMIN is a platform role and belongs to no single business.
                    .tenant(seed.level() == SeedRoles.LEVEL_PLATFORM ? null : tenant)
                    .department(seed.departmentCode() == null ? null : departments.get(seed.departmentCode()))
                    .systemRole(true)
                    .permissions(granted)
                    .build();

            byCode.put(seed.code(), roleRepository.save(role));
        }
        log.info("Seeded {} roles", byCode.size());
        return byCode;
    }

    private List<User> seedUsers(Tenant tenant,
                                 Map<String, Role> roles,
                                 Map<String, Department> departments,
                                 List<Branch> branches) {
        List<User> saved = new ArrayList<>();
        int index = 0;

        for (SeedUsers.UserSeed seed : SeedUsers.all()) {
            Role role = roles.get(seed.roleCode());
            if (role == null) {
                throw new IllegalStateException("Unknown role " + seed.roleCode() + " for " + seed.email());
            }

            User user = User.builder()
                    .tenant(tenant)
                    .email(seed.email())
                    .username(seed.email().substring(0, seed.email().indexOf('@')))
                    .passwordHash(passwordEncoder.encode(seed.password()))
                    .firstName(seed.firstName())
                    .lastName(seed.lastName())
                    .phone(seed.phone())
                    .nationalId(String.valueOf(28000000 + index * 137))
                    .employeeNumber(String.format("EMP-%03d", index + 1))
                    .position(seed.position())
                    .department(seed.departmentCode() == null
                            ? departments.get("EXE")
                            : departments.get(seed.departmentCode()))
                    // Spread staff across branches so branch filters are meaningful.
                    .branch(branches.get(index % branches.size()))
                    .employmentDate(LocalDate.now().minusMonths(24 - index))
                    .roles(new HashSet<>(Set.of(role)))
                    .status(seed.forcePasswordChange() ? UserStatus.PENDING_INVITATION : UserStatus.ACTIVE)
                    .firstLogin(seed.forcePasswordChange())
                    .platformAdmin("SUPER_ADMIN".equals(seed.roleCode()))
                    .passwordChangedAt(seed.forcePasswordChange() ? null : LocalDateTime.now().minusDays(30))
                    .lastLoginAt(seed.forcePasswordChange() ? null : LocalDateTime.now().minusHours(index + 1L))
                    .lastLoginIp("197.232.14." + (20 + index))
                    .build();

            saved.add(userRepository.save(user));
            index++;
        }
        log.info("Seeded {} users", saved.size());
        return saved;
    }

    /** Department heads, so the "who reports to whom" view is populated. */
    private void setDepartmentHeads(Map<String, Department> departments, Map<String, User> byRole) {
        assignHead(departments, "FIN", byRole.get("FINANCE_MANAGER"));
        assignHead(departments, "HR", byRole.get("HR_MANAGER"));
        assignHead(departments, "OPS", byRole.get("INVENTORY_MANAGER"));
        assignHead(departments, "SLS", byRole.get("SALES_MANAGER"));
        assignHead(departments, "EXE", byRole.get("BUSINESS_OWNER"));
    }

    private void assignHead(Map<String, Department> departments, String code, User head) {
        Department department = departments.get(code);
        if (department != null && head != null) {
            department.setHead(head);
            departmentRepository.save(department);
        }
    }

    /**
     * Invitation records for every account. In production these are emailed; with
     * no SMTP in the demo the rendered body is stored and shown in the admin UI,
     * which is also what makes the first-login flow testable.
     */
    private void seedInvitations(Tenant tenant, List<User> users, User owner) {
        List<UserInvitation> invitations = new ArrayList<>();

        for (SeedUsers.UserSeed seed : SeedUsers.all()) {
            User user = users.stream()
                    .filter(candidate -> candidate.getEmail().equals(seed.email()))
                    .findFirst()
                    .orElse(null);
            if (user == null || user.equals(owner)) {
                continue;
            }

            boolean pending = seed.forcePasswordChange();
            invitations.add(UserInvitation.builder()
                    .tenant(tenant)
                    .user(user)
                    .token(UUID.randomUUID().toString())
                    .temporaryPassword(pending ? seed.password() : null)
                    .invitedBy(owner)
                    .status(pending ? ApprovalStatus.PENDING : ApprovalStatus.APPROVED)
                    .expiresAt(LocalDateTime.now().plusDays(pending ? 7 : -1))
                    .acceptedAt(pending ? null : LocalDateTime.now().minusDays(20))
                    .emailBody(renderInvitation(tenant, user, seed))
                    .build());
        }

        invitationRepository.saveAll(invitations);
        log.info("Seeded {} user invitations", invitations.size());

        if (owner != null) {
            auditService.recordAs(owner, tenant, "SEED_TENANT", "Administration",
                    "Tenant", tenant.getId(), tenant.getName(),
                    "Demo business provisioned with " + users.size() + " users");
        }
    }

    private String renderInvitation(Tenant tenant, User user, SeedUsers.UserSeed seed) {
        return """
                Welcome to BIASHARA

                Hello %s,

                An account has been created for you.

                Company:  %s
                Role:     %s
                Login:    %s
                Password: %s

                Sign in at http://localhost:5173/login

                For security reasons you must change your password after your first login.
                """.formatted(
                user.getFullName(),
                tenant.getName(),
                seed.position(),
                user.getEmail(),
                seed.forcePasswordChange() ? seed.password() : "(already changed)");
    }
}
