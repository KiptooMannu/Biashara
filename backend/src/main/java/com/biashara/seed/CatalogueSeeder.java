package com.biashara.seed;

import com.biashara.common.enums.ProductType;
import com.biashara.iam.domain.Tenant;
import com.biashara.inventory.domain.Category;
import com.biashara.inventory.domain.Product;
import com.biashara.inventory.domain.Warehouse;
import com.biashara.inventory.repository.CategoryRepository;
import com.biashara.inventory.repository.ProductRepository;
import com.biashara.inventory.repository.WarehouseRepository;
import com.biashara.procurement.domain.Supplier;
import com.biashara.procurement.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Stages 4-6: categories, warehouses, suppliers and the product catalogue. */
@Component
@RequiredArgsConstructor
public class CatalogueSeeder {

    private static final Logger log = LoggerFactory.getLogger(CatalogueSeeder.class);

    /** Fixed seed: the demo data is the same on every machine and every run. */
    private static final long RANDOM_SEED = 20260726L;

    private final CategoryRepository categoryRepository;
    private final WarehouseRepository warehouseRepository;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;

    public record CatalogueContext(
            List<Category> categories,
            List<Warehouse> warehouses,
            List<Supplier> suppliers,
            List<Product> products) {
    }

    /** name, category, unit, buying price, selling price, min stock, opening stock. */
    private record ProductSeed(String name, String category, String unit,
                               String buying, String selling, int minStock, int stock) {
    }

    @Transactional
    public CatalogueContext seed(Tenant tenant) {
        Random random = new Random(RANDOM_SEED);

        List<Category> categories = seedCategories(tenant);
        Map<String, Category> byName = new HashMap<>();
        categories.forEach(category -> byName.put(category.getName(), category));

        List<Warehouse> warehouses = seedWarehouses(tenant);
        List<Supplier> suppliers = seedSuppliers(tenant, random);
        List<Product> products = seedProducts(tenant, byName, warehouses, suppliers, random);

        return new CatalogueContext(categories, warehouses, suppliers, products);
    }

    private List<Category> seedCategories(Tenant tenant) {
        // Colours are fixed per category so a category keeps the same colour in
        // every chart across the whole application.
        String[][] definitions = {
                {"Beverages", "#2563eb"}, {"Dairy", "#0891b2"}, {"Cooking Oil", "#ca8a04"},
                {"Rice & Grains", "#a16207"}, {"Flour", "#d97706"}, {"Sugar", "#e11d48"},
                {"Snacks", "#f59e0b"}, {"Frozen Foods", "#0ea5e9"}, {"Vegetables", "#16a34a"},
                {"Fruits", "#84cc16"}, {"Bakery", "#b45309"}, {"Electronics", "#6366f1"},
                {"Stationery", "#8b5cf6"}, {"Cleaning", "#06b6d4"}, {"Toiletries", "#ec4899"},
                {"Medicine", "#dc2626"}, {"Cosmetics", "#db2777"}, {"Hardware", "#64748b"},
                {"Animal Feed", "#78716c"}, {"Baby Products", "#f472b6"}};

        List<Category> batch = new ArrayList<>();
        int index = 1;
        for (String[] definition : definitions) {
            batch.add(Category.builder()
                    .tenant(tenant)
                    .name(definition[0])
                    .code(String.format("CAT-%02d", index++))
                    .colour(definition[1])
                    .description(definition[0] + " product line")
                    .build());
        }
        List<Category> saved = categoryRepository.saveAll(batch);
        log.info("Seeded {} categories", saved.size());
        return saved;
    }

    /**
     * Storage locations across the chain. Warehouse transfers are a headline
     * inventory feature, so there need to be enough locations to transfer between.
     * Index 0 is the default store and index 1 is the cold room — the product
     * seeder relies on that ordering.
     */
    private List<Warehouse> seedWarehouses(Tenant tenant) {
        String[][] definitions = {
                {"Main Store", "Ngong Road — ground floor"},
                {"Cold Room", "Ngong Road — rear"},
                {"Overflow Godown", "Industrial Area"},
                {"Westlands Store", "Westlands branch"},
                {"Thika Road Store", "Thika Road Mall branch"},
                {"Karen Store", "Karen branch"},
                {"Central Distribution Centre", "Industrial Area — Likoni Road"},
                {"Nakuru Store", "Nakuru Town branch"},
                {"Mombasa Store", "Nyali branch"},
                {"Bonded Warehouse", "Mombasa Port"}};

        List<Warehouse> batch = new ArrayList<>();
        int index = 1;
        for (String[] definition : definitions) {
            batch.add(Warehouse.builder()
                    .tenant(tenant)
                    .name(definition[0])
                    .code(String.format("WH-%03d", index))
                    .location(definition[1])
                    .defaultWarehouse(index == 1)
                    .build());
            index++;
        }

        List<Warehouse> saved = warehouseRepository.saveAll(batch);
        log.info("Seeded {} warehouses", saved.size());
        return saved;
    }

    private List<Supplier> seedSuppliers(Tenant tenant, Random random) {
        String[][] definitions = {
                {"ABC Distributors", "Joseph Kamau", "Beverages and dry goods"},
                {"Ken Foods Ltd", "Lucy Wambui", "Packaged foods"},
                {"Fresh Farm Ltd", "Moses Kirui", "Fruit and vegetables"},
                {"Metro Supplies", "Anne Mueni", "General wholesale"},
                {"Prime Traders", "Victor Ochieng", "Rice, flour and sugar"},
                {"Highland Dairies", "Rose Chelagat", "Milk and dairy"},
                {"Coastal Oils", "Ali Hassan", "Cooking oils"},
                {"Nairobi Bakers", "Paul Mwangi", "Bread and bakery"},
                {"BrightTech Kenya", "Dennis Ouma", "Electronics and batteries"},
                {"CleanPro Chemicals", "Janet Akinyi", "Cleaning and detergents"},
                {"PharmaLink", "Dr. Susan Wairimu", "Over-the-counter medicine"},
                {"GlowCare Cosmetics", "Nancy Kirop", "Cosmetics and toiletries"},
                {"Rift Valley Feeds", "Simon Tanui", "Animal feed"},
                {"BabyJoy Imports", "Miriam Odhiambo", "Baby products"},
                {"Hardware House", "Eric Muriuki", "Hardware and tools"},
                {"PaperTrail Stationers", "Beatrice Nyaga", "Stationery"},
                {"FrostLine Frozen", "Charles Kiplagat", "Frozen foods"},
                {"Sweet Valley Sugar", "Grace Atieno", "Sugar and sweeteners"},
                {"Java Roasters", "Tom Njoroge", "Coffee and tea"},
                {"AquaPure Water", "Hellen Mutindi", "Bottled water"}};

        List<Supplier> batch = new ArrayList<>();
        int index = 1;
        for (String[] definition : definitions) {
            int leadTime = 2 + random.nextInt(7);
            int totalOrders = 8 + random.nextInt(30);
            int late = random.nextInt(Math.max(1, totalOrders / 4));

            // Reliability falls as late deliveries rise — a derived score, not a guess.
            BigDecimal onTimeRate = BigDecimal.valueOf(totalOrders - late)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP);

            batch.add(Supplier.builder()
                    .tenant(tenant)
                    .name(definition[0])
                    .code(String.format("SUP-%03d", index))
                    .contactPerson(definition[1])
                    .phone("+254 7" + (20 + random.nextInt(19)) + " " + (100000 + random.nextInt(899999)))
                    .email(definition[0].toLowerCase().replaceAll("[^a-z]", "") + "@supplier.co.ke")
                    .address("Nairobi, Kenya")
                    .city("Nairobi")
                    .taxPin("P05" + (1000000 + random.nextInt(8999999)) + "X")
                    .leadTimeDays(leadTime)
                    .averageDeliveryDays(BigDecimal.valueOf(leadTime + random.nextInt(3) - 1)
                            .max(BigDecimal.ONE).setScale(2, RoundingMode.HALF_UP))
                    .reliabilityScore(onTimeRate)
                    .rating(Math.max(1, (int) Math.round(onTimeRate.doubleValue() / 20)))
                    .totalOrders(totalOrders)
                    .lateDeliveries(late)
                    .totalPurchaseValue(BigDecimal.valueOf((50 + random.nextInt(400)) * 1000L))
                    .outstandingBalance(random.nextBoolean()
                            ? BigDecimal.valueOf(random.nextInt(180) * 1000L)
                            : BigDecimal.ZERO)
                    .paymentTerms(random.nextBoolean() ? "Net 30" : "Net 14")
                    .notes(definition[2])
                    .build());
            index++;
        }
        List<Supplier> saved = supplierRepository.saveAll(batch);
        log.info("Seeded {} suppliers", saved.size());
        return saved;
    }

    private List<Product> seedProducts(Tenant tenant,
                                       Map<String, Category> categories,
                                       List<Warehouse> warehouses,
                                       List<Supplier> suppliers,
                                       Random random) {

        List<ProductSeed> definitions = List.of(
                new ProductSeed("Fresh Milk 500ml", "Dairy", "pc", "55", "70", 60, 240),
                new ProductSeed("Long Life Milk 1L", "Dairy", "pc", "110", "140", 40, 180),
                new ProductSeed("Yoghurt Vanilla 250ml", "Dairy", "pc", "60", "85", 30, 96),
                new ProductSeed("Butter 250g", "Dairy", "pc", "230", "290", 20, 54),
                new ProductSeed("Cheddar Cheese 200g", "Dairy", "pc", "320", "410", 15, 38),
                new ProductSeed("White Bread 400g", "Bakery", "pc", "55", "70", 50, 120),
                new ProductSeed("Brown Bread 400g", "Bakery", "pc", "60", "75", 40, 88),
                new ProductSeed("Queen Cakes 6pk", "Bakery", "pk", "90", "120", 20, 45),
                new ProductSeed("Pishori Rice 2kg", "Rice & Grains", "pk", "380", "460", 30, 140),
                new ProductSeed("Basmati Rice 1kg", "Rice & Grains", "pk", "290", "360", 20, 76),
                new ProductSeed("Green Grams 1kg", "Rice & Grains", "pk", "180", "230", 25, 92),
                new ProductSeed("Maize Flour 2kg", "Flour", "pk", "165", "200", 60, 260),
                new ProductSeed("Wheat Flour 2kg", "Flour", "pk", "185", "225", 40, 150),
                new ProductSeed("Porridge Flour 1kg", "Flour", "pk", "140", "180", 25, 84),
                new ProductSeed("White Sugar 2kg", "Sugar", "pk", "290", "350", 50, 210),
                new ProductSeed("Brown Sugar 1kg", "Sugar", "pk", "170", "215", 20, 62),
                new ProductSeed("Cooking Oil 2L", "Cooking Oil", "btl", "540", "650", 40, 18),
                new ProductSeed("Cooking Oil 5L", "Cooking Oil", "btl", "1280", "1520", 20, 34),
                new ProductSeed("Cooking Fat 1kg", "Cooking Oil", "pc", "290", "360", 20, 58),
                new ProductSeed("Tea Leaves 500g", "Beverages", "pk", "310", "395", 30, 130),
                new ProductSeed("Instant Coffee 200g", "Beverages", "jar", "620", "780", 15, 44),
                new ProductSeed("Drinking Chocolate 400g", "Beverages", "tin", "480", "600", 12, 30),
                new ProductSeed("Soda 500ml", "Beverages", "btl", "55", "75", 100, 420),
                new ProductSeed("Mineral Water 1L", "Beverages", "btl", "40", "60", 80, 340),
                new ProductSeed("Mango Juice 1L", "Beverages", "pk", "150", "195", 30, 110),
                new ProductSeed("Biscuits Assorted 200g", "Snacks", "pk", "85", "115", 40, 175),
                new ProductSeed("Crisps 150g", "Snacks", "pk", "95", "130", 30, 128),
                new ProductSeed("Groundnuts 250g", "Snacks", "pk", "110", "150", 25, 70),
                new ProductSeed("Frozen Sausages 500g", "Frozen Foods", "pk", "340", "430", 20, 46),
                new ProductSeed("Frozen Chicken 1kg", "Frozen Foods", "pc", "520", "640", 15, 32),
                new ProductSeed("Frozen Peas 500g", "Frozen Foods", "pk", "180", "235", 15, 40),
                new ProductSeed("Tomatoes 1kg", "Vegetables", "kg", "90", "130", 30, 24),
                new ProductSeed("Onions 1kg", "Vegetables", "kg", "80", "120", 30, 68),
                new ProductSeed("Sukuma Wiki bunch", "Vegetables", "bunch", "20", "35", 40, 90),
                new ProductSeed("Potatoes 2kg", "Vegetables", "pk", "150", "200", 25, 74),
                new ProductSeed("Bananas 1kg", "Fruits", "kg", "90", "130", 25, 56),
                new ProductSeed("Oranges 1kg", "Fruits", "kg", "110", "155", 20, 48),
                new ProductSeed("Watermelon whole", "Fruits", "pc", "180", "250", 12, 26),
                new ProductSeed("Bar Soap 800g", "Toiletries", "pc", "150", "190", 40, 165),
                new ProductSeed("Toothpaste 140ml", "Toiletries", "pc", "180", "230", 30, 118),
                new ProductSeed("Toilet Paper 4pk", "Toiletries", "pk", "170", "220", 35, 140),
                new ProductSeed("Laundry Detergent 1kg", "Cleaning", "pk", "260", "330", 30, 96),
                new ProductSeed("Dishwashing Liquid 750ml", "Cleaning", "btl", "195", "250", 25, 82),
                new ProductSeed("Disinfectant 1L", "Cleaning", "btl", "290", "370", 20, 58),
                new ProductSeed("AA Batteries 4pk", "Electronics", "pk", "180", "250", 20, 64),
                new ProductSeed("LED Bulb 9W", "Electronics", "pc", "220", "300", 20, 72),
                new ProductSeed("Phone Charger USB-C", "Electronics", "pc", "450", "650", 12, 28),
                new ProductSeed("Exercise Book 200pg", "Stationery", "pc", "65", "90", 60, 240),
                new ProductSeed("Ballpoint Pens 10pk", "Stationery", "pk", "120", "170", 30, 105),
                new ProductSeed("Baby Diapers Medium 30pk", "Baby Products", "pk", "980", "1200", 15, 36),
                new ProductSeed("Baby Wipes 80pk", "Baby Products", "pk", "260", "340", 20, 52),
                new ProductSeed("Paracetamol 500mg 20s", "Medicine", "pk", "60", "95", 25, 88),
                new ProductSeed("Body Lotion 400ml", "Cosmetics", "btl", "340", "440", 20, 54),
                new ProductSeed("Layer Mash 20kg", "Animal Feed", "bag", "1850", "2200", 10, 22),
                new ProductSeed("Padlock 50mm", "Hardware", "pc", "280", "380", 12, 30));

        List<Product> batch = new ArrayList<>();
        int index = 1;
        for (ProductSeed seed : definitions) {
            Category category = categories.get(seed.category());
            BigDecimal buying = new BigDecimal(seed.buying());

            // Perishables carry an expiry date; general merchandise does not.
            boolean perishable = List.of("Dairy", "Bakery", "Vegetables", "Fruits",
                    "Frozen Foods", "Medicine").contains(seed.category());

            batch.add(Product.builder()
                    .tenant(tenant)
                    .sku(String.format("GM-%04d", index))
                    .barcode("616" + String.format("%010d", 1000000 + index * 7919))
                    .name(seed.name())
                    .description(seed.name() + " — " + seed.category())
                    .category(category)
                    .supplier(suppliers.get(index % suppliers.size()))
                    // Frozen and dairy live in the cold room.
                    .warehouse(List.of("Frozen Foods", "Dairy").contains(seed.category())
                            ? warehouses.get(1)
                            : warehouses.get(0))
                    .productType(ProductType.PHYSICAL)
                    .unit(seed.unit())
                    .buyingPrice(buying)
                    .sellingPrice(new BigDecimal(seed.selling()))
                    .vatRate(List.of("Medicine", "Vegetables", "Fruits").contains(seed.category())
                            ? BigDecimal.ZERO
                            : new BigDecimal("16.00"))
                    .currentStock(seed.stock())
                    .minStock(seed.minStock())
                    .maxStock(seed.minStock() * 8)
                    .reorderLevel((int) Math.round(seed.minStock() * 1.5))
                    .expiryDate(perishable
                            ? LocalDate.now().plusDays(3 + random.nextInt(120))
                            : null)
                    .active(true)
                    // Real velocity is recalculated from seeded sales afterwards; this
                    // is only a starting value so nothing is null before that runs.
                    .salesVelocity(BigDecimal.ZERO)
                    .build());
            index++;
        }

        List<Product> saved = productRepository.saveAll(batch);
        log.info("Seeded {} products", saved.size());
        return saved;
    }
}
