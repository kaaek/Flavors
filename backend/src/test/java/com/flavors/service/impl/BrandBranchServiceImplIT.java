package com.flavors.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.flavors.TestcontainersConfiguration;
import com.flavors.dto.BranchRegistrationRequest;
import com.flavors.dto.BrandRegistrationRequest;
import com.flavors.repository.BranchRepository;
import com.flavors.service.BrandBranchService;
import com.flavors.service.BrandRegistrationService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class BrandBranchServiceImplIT {

    @Autowired
    BrandRegistrationService registration;

    @Autowired
    BrandBranchService brandBranches;

    @Autowired
    BranchRepository branches;

    @Test
    void addsAnotherBranchUnderTheExistingBrandAccount() {
        long brandId = registration.register(new BrandRegistrationRequest(
                "Karim's Kitchen", "Karim", UUID.randomUUID() + "@example.com",
                "+961 1 234567", "correct horse",
                List.of(new BranchRegistrationRequest("Hamra", "Beirut")))).id();

        var added = brandBranches.add(brandId, new BranchRegistrationRequest("Jounieh", "Keserwan"));

        assertThat(added.id()).isNotNull();
        assertThat(brandBranches.list(brandId)).extracting("name")
                .containsExactly("Hamra", "Jounieh");
        assertThat(branches.findByBrandId(brandId)).hasSize(2);
    }
}
