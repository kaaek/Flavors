package com.flavors.repository;
 
import com.flavors.entity.CreatorProfile;
import com.flavors.enums.VerificationStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
 
/** Look a profile up with {@code findById(userId)}: the id is the creator's user id. */
public interface CreatorProfileRepository extends JpaRepository<CreatorProfile, Long> {
 
    /** The admin queue (UC-F20.1): profiles in one state, oldest first. */
    List<CreatorProfile> findByVerificationStatusOrderByCreatedAtAsc(VerificationStatus status);
}
