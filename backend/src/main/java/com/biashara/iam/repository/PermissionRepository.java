package com.biashara.iam.repository;

import com.biashara.iam.domain.Permission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PermissionRepository extends JpaRepository<Permission, Long> {

    Optional<Permission> findByCode(String code);

    List<Permission> findAllByOrderByModuleAscCodeAsc();

    List<Permission> findByModule(String module);

    boolean existsByCode(String code);
}
