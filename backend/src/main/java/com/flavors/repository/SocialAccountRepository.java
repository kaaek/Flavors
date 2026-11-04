package com.flavors.repository;

import com.flavors.entity.SocialAccount;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** The token is not reachable from here on purpose; see SocialAccountTokenRepository. */
public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {

    /** All accounts a creator connected, for her portfolio and her Manage Portfolio page. */
    List<SocialAccount> findByCreatorId(Long creatorId);

    /** One of her own accounts; someone else's account is "not found" (NF-1). */
    Optional<SocialAccount> findByIdAndCreatorId(Long id, Long creatorId);

    /** The account with this id on the platform, whoever connected it (the pair is unique, V2). */
    Optional<SocialAccount> findByPlatformAndExternalId(String platform, String externalId);
}
