package com.flavors.repository;

import com.flavors.entity.SocialPostMetric;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SocialPostMetricRepository extends JpaRepository<SocialPostMetric, Long> {

    /** The stored figures of one post, so a new pull overwrites them instead of adding a row. */
    Optional<SocialPostMetric> findBySocialAccountIdAndExternalPostId(Long socialAccountId, String externalPostId);

    List<SocialPostMetric> findBySocialAccountId(Long socialAccountId);
}
