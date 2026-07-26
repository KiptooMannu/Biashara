package com.biashara.seed;

import com.biashara.common.enums.EmploymentType;
import com.biashara.crm.domain.Customer;
import com.biashara.crm.repository.CustomerRepository;
import com.biashara.hr.domain.Employee;
import com.biashara.hr.repository.EmployeeRepository;
import com.biashara.iam.domain.Tenant;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Stages 7-8: customers and employees.
 *
 * Tier, churn risk and lifetime value are left null here and computed by the
 * analytics pass once sales exist — assigning them up front would make the CRM
 * scoring look like decoration rather than a calculation.
 */
@Component
@RequiredArgsConstructor
public class PartnerSeeder {

    private static final Logger log = LoggerFactory.getLogger(PartnerSeeder.class);
    private static final long RANDOM_SEED = 771026L;

    private final CustomerRepository customerRepository;
    private final EmployeeRepository employeeRepository;
    private final jakarta.persistence.EntityManager entityManager;

    public record PartnerContext(List<Customer> customers, List<Employee> employees) {
    }

    @Transactional
    public PartnerContext seed(Tenant tenant, IamSeeder.IamContext iam) {
        Random random = new Random(RANDOM_SEED);
        List<Customer> customers = seedCustomers(tenant, random);
        List<Employee> employees = seedEmployees(tenant, iam, random);
        return new PartnerContext(customers, employees);
    }

    private List<Customer> seedCustomers(Tenant tenant, Random random) {
        String[][] definitions = {
                {"John Mwangi", "INDIVIDUAL"}, {"Mary Wanjiku", "INDIVIDUAL"},
                {"David Otieno", "INDIVIDUAL"}, {"Alice Njeri", "INDIVIDUAL"},
                {"Peter Kiptoo", "INDIVIDUAL"}, {"Esther Muthoni", "INDIVIDUAL"},
                {"Joseph Kimani", "INDIVIDUAL"}, {"Caroline Adhiambo", "INDIVIDUAL"},
                {"Samuel Wekesa", "INDIVIDUAL"}, {"Rebecca Chepkoech", "INDIVIDUAL"},
                {"Anthony Mutua", "INDIVIDUAL"}, {"Winnie Akoth", "INDIVIDUAL"},
                {"Dennis Rotich", "INDIVIDUAL"}, {"Priscilla Wangari", "INDIVIDUAL"},
                {"Michael Barasa", "INDIVIDUAL"},
                {"Nairobi Hotel Group", "BUSINESS"}, {"St. Mary's School", "BUSINESS"},
                {"Kilimani Cafe Ltd", "BUSINESS"}, {"Riverside Apartments", "BUSINESS"},
                {"Uhuru Catering Services", "BUSINESS"}, {"Westgate Kiosk", "BUSINESS"},
                {"Green Valley Academy", "BUSINESS"}};

        List<Customer> batch = new ArrayList<>();
        int index = 1;
        for (String[] definition : definitions) {
            boolean business = "BUSINESS".equals(definition[1]);

            // Businesses buy on credit; walk-in individuals mostly do not.
            BigDecimal creditLimit = business
                    ? BigDecimal.valueOf((50 + random.nextInt(20) * 10) * 1000L)
                    : (random.nextInt(3) == 0 ? BigDecimal.valueOf(10000) : BigDecimal.ZERO);

            BigDecimal outstanding = creditLimit.signum() > 0 && random.nextBoolean()
                    ? creditLimit.multiply(BigDecimal.valueOf(random.nextInt(70) + 10))
                    .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            batch.add(Customer.builder()
                    .tenant(tenant)
                    .name(definition[0])
                    .phone("+254 7" + (10 + random.nextInt(29)) + " " + (100000 + random.nextInt(899999)))
                    .email(definition[0].toLowerCase().replaceAll("[^a-z]", ".") + "@mail.co.ke")
                    .address(business ? "Westlands, Nairobi" : "Nairobi")
                    .city("Nairobi")
                    .customerType(definition[1])
                    .taxPin(business ? "P05" + (1000000 + random.nextInt(8999999)) + "Q" : null)
                    .loyaltyPoints(random.nextInt(900))
                    .creditLimit(creditLimit)
                    .outstandingBalance(outstanding)
                    .totalSpent(BigDecimal.ZERO)
                    .totalOrders(0)
                    .averageOrderValue(BigDecimal.ZERO)
                    .birthday(LocalDate.now().minusYears(22 + random.nextInt(35))
                            .withDayOfYear(1 + random.nextInt(360)))
                    .notes(business ? "Wholesale account, invoiced monthly" : null)
                    .active(true)
                    .build());
            index++;
        }

        List<Customer> saved = customerRepository.saveAll(batch);
        spreadRegistrationDates(saved);
        log.info("Seeded {} customers", saved.size());
        return saved;
    }

    /**
     * Backdates each customer's created_at across the reporting window.
     *
     * Needed because {@code @CreatedDate} stamps the insert with "now", which would
     * put every customer on the same day and reduce the customer-growth chart to a
     * single point. The column is {@code updatable = false}, so this has to go
     * through a native statement rather than a normal save.
     */
    private void spreadRegistrationDates(List<Customer> customers) {
        int index = 0;
        for (Customer customer : customers) {
            // Oldest accounts first, thinning out towards today.
            long daysAgo = 88L - (long) index * (80 / Math.max(1, customers.size()));
            entityManager.createNativeQuery(
                            "update customers set created_at = :registeredOn where id = :id")
                    .setParameter("registeredOn", LocalDate.now().minusDays(Math.max(1, daysAgo)).atTime(9, 30))
                    .setParameter("id", customer.getId())
                    .executeUpdate();
            index++;
        }
    }

    private List<Employee> seedEmployees(Tenant tenant, IamSeeder.IamContext iam, Random random) {
        /* name, position, department code, basic salary, employment type */
        String[][] definitions = {
                {"Brian", "Otieno", "Till Operator", "SLS", "38000", "PERMANENT"},
                {"Nancy", "Wairimu", "Till Operator", "SLS", "36000", "PERMANENT"},
                {"Collins", "Ouma", "Till Operator", "SLS", "35000", "CONTRACT"},
                {"Sharon", "Kerubo", "Sales Assistant", "SLS", "32000", "PERMANENT"},
                {"Elijah", "Maina", "Sales Assistant", "SLS", "31000", "CASUAL"},
                {"Samuel", "Barasa", "Storekeeper", "OPS", "42000", "PERMANENT"},
                {"Agnes", "Nyambura", "Store Assistant", "OPS", "33000", "PERMANENT"},
                {"Fredrick", "Kiprono", "Warehouse Officer", "OPS", "45000", "PERMANENT"},
                {"Mary", "Achieng", "Accountant", "FIN", "72000", "PERMANENT"},
                {"Joel", "Mwenda", "Accounts Assistant", "FIN", "48000", "PERMANENT"},
                {"Cynthia", "Wafula", "HR Officer", "HR", "55000", "PERMANENT"},
                {"Patrick", "Njuguna", "Security Guard", "SEC", "28000", "CONTRACT"},
                {"Zipporah", "Kilonzo", "Security Guard", "SEC", "28000", "CONTRACT"},
                {"Isaac", "Mutiso", "Cleaner", "MNT", "24000", "CASUAL"},
                {"Beatrice", "Owuor", "Cleaner", "MNT", "24000", "CASUAL"},
                {"Timothy", "Kilelu", "Maintenance Technician", "MNT", "46000", "PERMANENT"},
                {"Lydia", "Chemutai", "Customer Service Officer", "CS", "38000", "PERMANENT"},
                {"George", "Ndungu", "IT Support Officer", "IT", "58000", "PERMANENT"},
                {"Rachael", "Simiyu", "Marketing Assistant", "MKT", "40000", "CONTRACT"},
                {"Vincent", "Kimathi", "Butchery Attendant", "OPS", "34000", "PERMANENT"},
                {"Doris", "Nekesa", "Bakery Attendant", "OPS", "33000", "PERMANENT"},
                {"Alfred", "Kiplimo", "Driver", "OPS", "39000", "PERMANENT"}};

        List<Employee> batch = new ArrayList<>();
        int index = 1;
        for (String[] definition : definitions) {
            BigDecimal basic = new BigDecimal(definition[4]);

            batch.add(Employee.builder()
                    .tenant(tenant)
                    .employeeNumber(String.format("GM-EMP-%03d", index))
                    .firstName(definition[0])
                    .lastName(definition[1])
                    .email(definition[0].toLowerCase() + "." + definition[1].toLowerCase() + "@greenmart.co.ke")
                    .phone("+254 7" + (30 + random.nextInt(19)) + " " + (100000 + random.nextInt(899999)))
                    .nationalId(String.valueOf(30000000 + index * 971))
                    .department(iam.departments().get(definition[3]))
                    .branch(iam.branches().get(index % iam.branches().size()))
                    .position(definition[2])
                    .employmentType(EmploymentType.valueOf(definition[5]))
                    .hireDate(LocalDate.now().minusMonths(2 + random.nextInt(46)))
                    .basicSalary(basic)
                    // Allowances run roughly 15-25% of basic pay.
                    .allowances(basic.multiply(BigDecimal.valueOf(15 + random.nextInt(11)))
                            .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP))
                    .performanceScore(BigDecimal.valueOf(62 + random.nextInt(36)))
                    .leaveBalance(BigDecimal.valueOf(random.nextInt(22)))
                    .commissionRate("SLS".equals(definition[3])
                            ? BigDecimal.valueOf(1 + random.nextInt(3))
                            : BigDecimal.ZERO)
                    .bankName(random.nextBoolean() ? "Equity Bank" : "KCB Bank")
                    .bankAccount(String.valueOf(1100000000L + random.nextInt(89999999)))
                    .nssfNumber("NSSF" + (200000 + index * 13))
                    .nhifNumber("NHIF" + (500000 + index * 17))
                    .taxPin("A00" + (1000000 + index * 3571) + "Z")
                    .active(true)
                    .build());
            index++;
        }

        List<Employee> saved = employeeRepository.saveAll(batch);

        // Link the employees who also hold a system login to their user account.
        linkUser(saved, "Brian", "Otieno", iam.cashier());
        linkUser(saved, "Samuel", "Barasa", iam.storekeeper());
        linkUser(saved, "Mary", "Achieng", null);

        log.info("Seeded {} employees", saved.size());
        return saved;
    }

    private void linkUser(List<Employee> employees, String firstName, String lastName,
                          com.biashara.iam.domain.User user) {
        if (user == null) {
            return;
        }
        employees.stream()
                .filter(employee -> employee.getFirstName().equals(firstName)
                        && employee.getLastName().equals(lastName))
                .findFirst()
                .ifPresent(employee -> {
                    employee.setUser(user);
                    employeeRepository.save(employee);
                });
    }
}
