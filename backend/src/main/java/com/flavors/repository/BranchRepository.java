package com.flavors.repository;

import com.flavors.entity.Branch;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BranchRepository extends JpaRepository<Branch, Long> {

    List<Branch> findByBrandId(Long brandId);

    List<Branch> findByBrandIdOrderByIdAsc(Long brandId);
}
