package com.flavors.service;

/**
 * Bytes read from private proof storage after the caller's campaign access was checked.
 *
 * <p>Like {@link ProofUpload}, the array is shared rather than copied, so a 5 MB download is not duplicated.
 */
public record ProofFileContent(byte[] bytes, String contentType) {
}
