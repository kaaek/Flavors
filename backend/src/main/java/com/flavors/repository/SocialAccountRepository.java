package com.flavors.repository;
 
import com.flavors.entity.SocialAccount;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
 
public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {
 
    /** All accounts a creator connected, for her portfolio and her Manage Portfolio page. */
    List<SocialAccount> findByCreatorId(Long creatorId);
}
 