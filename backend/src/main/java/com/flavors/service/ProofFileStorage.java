package com.flavors.service;

/**
 * Private object storage for delivery-proof screenshots. Database rows keep only the returned key;
 * this boundary lets local development use a folder while Railway uses its S3-compatible bucket.
 */
public interface ProofFileStorage {

    void store(String key, byte[] bytes, String contentType);

    ProofFileContent read(String key, String contentType);

    /** Used only for draft replacement and rollback cleanup; submitted proof has no delete operation. */
    void delete(String key);
}
