package com.biashara.inventory.repository;

import com.biashara.inventory.domain.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {

    List<Warehouse> findByTenantIdAndDeletedFalseOrderByNameAsc(Long tenantId);

    Optional<Warehouse> findByTenantIdAndDefaultWarehouseTrueAndDeletedFalse(Long tenantId);
}
