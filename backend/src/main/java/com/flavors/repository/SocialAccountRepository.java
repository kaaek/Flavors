package com.flavors.repository;

import com.flavors.entity.SocialAccount;
import com.flavors.enums.SocialAccountStatus;
import java.util.List;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** The token is not reachable from here on purpose; see SocialAccountTokenRepository. */
public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {

    /** All accounts a creator connected, for her portfolio and her Manage Portfolio page. */
    List<SocialAccount> findByCreatorId(Long creatorId);

    /** One of her own accounts; someone else's account is "not found" (NF-1). */
    Optional<SocialAccount> findByIdAndCreatorId(Long id, Long creatorId);

    /** The account with this id on the platform, whoever connected it (the pair is unique, V2). */
    Optional<SocialAccount> findByPlatformAndExternalId(String platform, String externalId);

    /**
     * The account, locked until the transaction ends (SELECT ... FOR UPDATE). Connect, disconnect and the daily
     * pull all take this lock before they read or change the token, so they run one at a time per account and a
     * stale pull result can't overwrite a disconnect that committed just before it.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from SocialAccount a where a.id = :id")
    Optional<SocialAccount> lockById(@Param("id") Long id);

    /** Only the ids, so the daily job doesn't load every account (and each pull then loads its own). */
    @Query("select a.id from SocialAccount a where a.status = :status order by a.id")
    List<Long> findIdsByStatus(@Param("status") SocialAccountStatus status);
}
