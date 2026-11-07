package com.flavors.dto;

import java.util.List;

/**
 * The editable parts of a proof draft. A null field means "leave it unchanged"; an empty list
 * deliberately clears that part. New screenshots arrive as separate multipart file parts.
 */
public record ProofDraftRequest(List<String> links, List<Long> retainedFileIds) {
}
