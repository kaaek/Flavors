package com.flavors.service;

/**
 * Untrusted screenshot bytes received by the web layer; the service validates their actual signature.
 *
 * <p>The array is shared, not copied: a proof can carry five 5 MB screenshots, and defensive copies at every
 * step would multiply that per request. Callers hand over a fresh array and must not modify it afterwards.
 */
public record ProofUpload(String originalFilename, String declaredContentType, byte[] bytes) {
}
