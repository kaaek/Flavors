package com.flavors.repository;

import com.flavors.entity.CreatorProfile;
import com.flavors.enums.VerificationStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Look a profile up with {@code findById(userId)}: the id is the creator's user id. */
public interface CreatorProfileRepository extends JpaRepository<CreatorProfile, Long> {

    /** The admin queue (UC-F20.1): profiles in one state, oldest first. */
    List<CreatorProfile> findByVerificationStatusOrderByCreatedAtAsc(VerificationStatus status);

    /**
     * Reads the profile and locks its row until the transaction ends, so two admins can't decide on
     * the same profile at once: the second one waits, then sees it was already reviewed.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CreatorProfile c where c.userId = :id")
    Optional<CreatorProfile> findByIdForUpdate(@Param("id") Long id);
}
