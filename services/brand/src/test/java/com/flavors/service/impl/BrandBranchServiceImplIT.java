package com.flavors.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.flavors.TestcontainersConfiguration;
import com.flavors.dto.BranchRegistrationRequest;
import com.flavors.repository.BranchRepository;
import com.flavors.service.BrandBranchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import com.flavors.support.BrandFixtures;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class BrandBranchServiceImplIT {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    BrandBranchService brandBranches;

    @Autowired
    BranchRepository branches;

    @Test
    void addsAnotherBranchUnderTheExistingBrandAccount() {
        long brandId = BrandFixtures.brand(jdbc, "Karim's Kitchen", null, null);

        var added = brandBranches.add(brandId, new BranchRegistrationRequest("Jounieh", "Keserwan"));

        assertThat(added.id()).isNotNull();
        assertThat(brandBranches.list(brandId)).extracting("name")
                .containsExactly("Hamra", "Jounieh");
        assertThat(branches.findByBrandId(brandId)).hasSize(2);
    }
}
