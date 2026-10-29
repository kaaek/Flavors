package com.flavors.repository;

import com.flavors.entity.AppUser;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    // lower() on both sides matches the unique index on lower(email) in V1, so the lookup uses it
    // and Bob@x.com finds bob@x.com.
    @Query("select u from AppUser u where lower(u.email) = lower(:email)")
    Optional<AppUser> findByEmailIgnoreCase(String email);
}
