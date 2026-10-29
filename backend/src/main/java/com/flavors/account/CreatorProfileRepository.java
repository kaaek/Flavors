package com.flavors.account;
 
import org.springframework.data.jpa.repository.JpaRepository;
 
/** Look a profile up with {@code findById(userId)}: the id is the creator's user id. */
public interface CreatorProfileRepository extends JpaRepository<CreatorProfile, Long> {
}