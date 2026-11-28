package com.flavors.client;

import com.flavors.enums.VerificationStatus;
import java.util.Optional;

/**
 * What the brand service needs to know about creators. Saving a creator requires that the creator exists and
 * is approved, and creators belong to the creator service.
 *
 * <p>TEMPORARY CROSS-READ (EEC-95 Phase 1): {@link JdbcCreatorDirectory} reads {@code creator_profile} in the
 * shared database. In the data-ownership phase an implementation that calls the creator service replaces it;
 * nothing else in this service changes. See docs/Microservices-Brand.md.
 */
public interface CreatorDirectory {

    /** The creator's approval status, or empty when no such creator exists. */
    Optional<VerificationStatus> verificationStatus(long creatorId);
}
