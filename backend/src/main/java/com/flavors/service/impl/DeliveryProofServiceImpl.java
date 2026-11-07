package com.flavors.service.impl;

import com.flavors.dto.DeliveryProofResponse;
import com.flavors.dto.ProofDraftRequest;
import com.flavors.entity.Campaign;
import com.flavors.entity.ProofFile;
import com.flavors.entity.ProofLink;
import com.flavors.entity.ProofSubmission;
import com.flavors.enums.CampaignStatus;
import com.flavors.enums.ProofSubmissionStatus;
import com.flavors.enums.Role;
import com.flavors.exception.DeliveryProofConflictException;
import com.flavors.exception.DeliveryProofNotFoundException;
import com.flavors.exception.InvalidDeliveryProofException;
import com.flavors.repository.CampaignRepository;
import com.flavors.repository.ProofFileRepository;
import com.flavors.repository.ProofLinkRepository;
import com.flavors.repository.ProofSubmissionRepository;
import com.flavors.service.AuditLogger;
import com.flavors.service.DeliveryProofService;
import com.flavors.service.ProofFileContent;
import com.flavors.service.ProofFileStorage;
import com.flavors.service.ProofUpload;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class DeliveryProofServiceImpl implements DeliveryProofService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeliveryProofServiceImpl.class);
    private static final int MAX_FILES = 5;
    private static final int MAX_LINKS = 10;
    private static final int MAX_URL_LENGTH = 2048;
    private static final Duration REVIEW_PERIOD = Duration.ofDays(5);

    private final CampaignRepository campaigns;
    private final ProofSubmissionRepository submissions;
    private final ProofLinkRepository links;
    private final ProofFileRepository files;
    private final ProofFileStorage storage;
    private final AuditLogger auditLogger;
    private final Clock clock;

    @Autowired
    public DeliveryProofServiceImpl(CampaignRepository campaigns, ProofSubmissionRepository submissions,
                                    ProofLinkRepository links, ProofFileRepository files,
                                    ProofFileStorage storage, AuditLogger auditLogger) {
        this(campaigns, submissions, links, files, storage, auditLogger, Clock.systemUTC());
    }

    DeliveryProofServiceImpl(CampaignRepository campaigns, ProofSubmissionRepository submissions,
                             ProofLinkRepository links, ProofFileRepository files,
                             ProofFileStorage storage, AuditLogger auditLogger, Clock clock) {
        this.campaigns = campaigns;
        this.submissions = submissions;
        this.links = links;
        this.files = files;
        this.storage = storage;
        this.auditLogger = auditLogger;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public DeliveryProofResponse getForCreator(long creatorId, long campaignId) {
        Campaign campaign = creatorCampaign(creatorId, campaignId, false);
        return response(campaign, true);
    }

    @Override
    @Transactional
    public DeliveryProofResponse saveDraft(long creatorId, long campaignId, ProofDraftRequest request,
                                           List<ProofUpload> newFiles) {
        Campaign campaign = creatorCampaign(creatorId, campaignId, true);
        requireEditable(campaign);
        StorageChanges storageChanges = registerStorageChanges();
        ProofSubmission draft = updateDraft(campaign, request, safeUploads(newFiles), storageChanges);
        auditLogger.record("PROOF_DRAFT_SAVED", "proof_submission", draft.getId(), Map.of(
                "campaignId", campaign.getId(), "round", draft.getRound()));
        return response(campaign, true);
    }

    @Override
    @Transactional
    public DeliveryProofResponse submit(long creatorId, long campaignId, ProofDraftRequest request,
                                        List<ProofUpload> newFiles) {
        Campaign campaign = creatorCampaign(creatorId, campaignId, true);
        requireEditable(campaign);
        StorageChanges storageChanges = registerStorageChanges();
        ProofSubmission draft = updateDraft(campaign, request, safeUploads(newFiles), storageChanges);

        List<ProofLink> finalLinks = links.findBySubmissionIdOrderById(draft.getId());
        validateFinalLinks(finalLinks);
        // Screenshots are optional: the agreement decides whether they are required. When present, updateDraft
        // already checked their count, size, MIME declaration and actual file signature.
        Instant submittedAt = clock.instant();
        draft.submit(submittedAt);
        campaign.markProofSubmitted(submittedAt, submittedAt.plus(REVIEW_PERIOD));

        auditLogger.record("PROOF_SUBMITTED", "proof_submission", draft.getId(), Map.of(
                "campaignId", campaign.getId(),
                "round", draft.getRound(),
                "links", finalLinks.size(),
                "files", files.findBySubmissionIdOrderById(draft.getId()).size()));
        return response(campaign, true);
    }

    @Override
    @Transactional(readOnly = true)
    public DeliveryProofResponse getForBrand(long brandId, long campaignId) {
        Campaign campaign = campaigns.findById(campaignId)
                .filter(found -> found.getBrandId().equals(brandId))
                .orElseThrow(DeliveryProofNotFoundException::new);
        return response(campaign, false);
    }

    @Override
    @Transactional(readOnly = true)
    public DeliveryProofResponse getForAdmin(long campaignId) {
        Campaign campaign = campaigns.findById(campaignId)
                .orElseThrow(DeliveryProofNotFoundException::new);
        return response(campaign, false);
    }

    @Override
    @Transactional(readOnly = true)
    public ProofFileContent download(long userId, Role role, long fileId) {
        ProofFile file = files.findById(fileId).orElseThrow(DeliveryProofNotFoundException::new);
        ProofSubmission submission = submissions.findById(file.getSubmissionId())
                .orElseThrow(DeliveryProofNotFoundException::new);
        Campaign campaign = campaigns.findById(submission.getCampaignId())
                .orElseThrow(DeliveryProofNotFoundException::new);

        boolean creatorOwns = role == Role.CREATOR && campaign.getCreatorId() != null
                && campaign.getCreatorId().equals(userId);
        boolean brandOwns = role == Role.BRAND && campaign.getBrandId().equals(userId);
        boolean admin = role == Role.ADMIN;
        boolean maySeeSubmitted = submission.getStatus() == ProofSubmissionStatus.SUBMITTED
                && (creatorOwns || brandOwns || admin);
        boolean maySeeDraft = submission.getStatus() == ProofSubmissionStatus.DRAFT && creatorOwns;
        if (!maySeeSubmitted && !maySeeDraft) {
            throw new DeliveryProofNotFoundException();
        }
        return storage.read(file.getStorageKey(), file.getContentType());
    }

    private Campaign creatorCampaign(long creatorId, long campaignId, boolean lock) {
        Campaign campaign = (lock ? campaigns.lockById(campaignId) : campaigns.findById(campaignId))
                .orElseThrow(DeliveryProofNotFoundException::new);
        if (campaign.getCreatorId() == null || !campaign.getCreatorId().equals(creatorId)) {
            throw new DeliveryProofNotFoundException();
        }
        return campaign;
    }

    private static void requireEditable(Campaign campaign) {
        if (campaign.getStatus() != CampaignStatus.ACTIVE
                && campaign.getStatus() != CampaignStatus.CORRECTIONS_REQUESTED) {
            throw new DeliveryProofConflictException(
                    "Proof can be edited only for an active campaign or after corrections are requested.");
        }
    }

    private ProofSubmission updateDraft(Campaign campaign, ProofDraftRequest request, List<ProofUpload> uploads,
                                        StorageChanges storageChanges) {
        int round = campaign.currentProofRound();
        ProofSubmission draft = submissions.findByCampaignIdAndStatus(campaign.getId(), ProofSubmissionStatus.DRAFT)
                .orElseGet(() -> submissions.saveAndFlush(new ProofSubmission(campaign.getId(), round)));
        if (draft.getRound() != round) {
            throw new DeliveryProofConflictException("The saved draft belongs to an earlier proof round.");
        }

        List<ProofLink> existingLinks = links.findBySubmissionIdOrderById(draft.getId());
        List<ProofFile> existingFiles = files.findBySubmissionIdOrderById(draft.getId());
        ProofDraftRequest change = request == null ? new ProofDraftRequest(null, null) : request;

        if (change.links() != null) {
            List<String> normalized = normalizeDraftLinks(change.links());
            links.deleteAll(existingLinks);
            links.saveAll(normalized.stream().map(url -> new ProofLink(draft.getId(), url)).toList());
        }

        Set<Long> retained = retainedIds(change.retainedFileIds(), existingFiles);
        List<ProofFile> removed = existingFiles.stream().filter(file -> !retained.contains(file.getId())).toList();
        if (retained.size() + uploads.size() > MAX_FILES) {
            throw new InvalidDeliveryProofException("A proof can contain at most 5 screenshots.");
        }
        List<ProofImageValidator.ValidatedImage> validated = uploads.stream()
                .map(ProofImageValidator::validate)
                .toList();

        files.deleteAll(removed);
        storageChanges.deleteAfterCommit.addAll(removed.stream().map(ProofFile::getStorageKey).toList());
        for (ProofImageValidator.ValidatedImage image : validated) {
            String key = "proof/" + campaign.getId() + "/" + draft.getId() + "/"
                    + UUID.randomUUID() + "." + image.extension();
            byte[] bytes = image.bytes();
            storage.store(key, bytes, image.contentType());
            storageChanges.deleteAfterRollback.add(key);
            files.save(new ProofFile(draft.getId(), key, image.contentType(), bytes.length));
        }
        return draft;
    }

    private static List<String> normalizeDraftLinks(List<String> requested) {
        // Without a cap, one request within the multipart limit could insert thousands of link rows.
        if (requested.size() > MAX_LINKS) {
            throw new InvalidDeliveryProofException("A proof can contain at most " + MAX_LINKS + " links.");
        }
        List<String> normalized = new ArrayList<>(requested.size());
        for (String value : requested) {
            String link = value == null ? "" : value.trim();
            if (link.length() > MAX_URL_LENGTH) {
                throw new InvalidDeliveryProofException("A proof link cannot exceed 2048 characters.");
            }
            normalized.add(link);
        }
        return List.copyOf(normalized);
    }

    private static Set<Long> retainedIds(List<Long> requested, List<ProofFile> existing) {
        Set<Long> existingIds = new HashSet<>();
        existing.forEach(file -> existingIds.add(file.getId()));
        if (requested == null) {
            return existingIds;
        }
        Set<Long> retained = new HashSet<>();
        for (Long id : requested) {
            if (id == null || !existingIds.contains(id)) {
                throw new InvalidDeliveryProofException("A retained screenshot does not belong to this draft.");
            }
            if (!retained.add(id)) {
                throw new InvalidDeliveryProofException("A retained screenshot was listed more than once.");
            }
        }
        return retained;
    }

    private static void validateFinalLinks(List<ProofLink> proofLinks) {
        if (proofLinks.isEmpty()) {
            throw new InvalidDeliveryProofException("Add at least one link before sending the proof.");
        }
        for (ProofLink proofLink : proofLinks) {
            String value = proofLink.getUrl();
            if (value == null || value.isBlank()) {
                throw new InvalidDeliveryProofException("Every proof link is required before sending.");
            }
            try {
                URI uri = new URI(value);
                String scheme = uri.getScheme();
                if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                        || uri.getHost() == null || uri.getHost().isBlank()) {
                    throw new InvalidDeliveryProofException("Every proof link must be a valid http or https URL.");
                }
            } catch (URISyntaxException e) {
                throw new InvalidDeliveryProofException("Every proof link must be a valid http or https URL.");
            }
        }
    }

    private DeliveryProofResponse response(Campaign campaign, boolean includeDraft) {
        List<DeliveryProofResponse.Submission> visible = submissions.findByCampaignIdOrderByRoundAsc(campaign.getId())
                .stream()
                .filter(submission -> includeDraft || submission.getStatus() == ProofSubmissionStatus.SUBMITTED)
                .map(this::submissionResponse)
                .toList();
        return new DeliveryProofResponse(campaign.getId(), campaign.getName(), campaign.getStatus(),
                campaign.getProofReviewDeadline(), visible);
    }

    private DeliveryProofResponse.Submission submissionResponse(ProofSubmission submission) {
        List<String> proofLinks = links.findBySubmissionIdOrderById(submission.getId()).stream()
                .map(ProofLink::getUrl)
                .toList();
        List<DeliveryProofResponse.File> proofFiles = files.findBySubmissionIdOrderById(submission.getId()).stream()
                .map(file -> new DeliveryProofResponse.File(file.getId(), file.getContentType(), file.getSizeBytes(),
                        "/api/proof-files/" + file.getId()))
                .toList();
        return new DeliveryProofResponse.Submission(submission.getId(), submission.getRound(), submission.getStatus(),
                submission.getCreatedAt(), submission.getSubmittedAt(), proofLinks, proofFiles);
    }

    private StorageChanges registerStorageChanges() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Delivery-proof storage changes require a transaction.");
        }
        StorageChanges changes = new StorageChanges();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                // An unknown result may have committed. Keep both sets rather than risk deleting evidence.
                List<String> cleanup = switch (status) {
                    case STATUS_COMMITTED -> changes.deleteAfterCommit;
                    case STATUS_ROLLED_BACK -> changes.deleteAfterRollback;
                    default -> List.of();
                };
                cleanup.forEach(DeliveryProofServiceImpl.this::deleteQuietly);
            }
        });
        return changes;
    }

    private void deleteQuietly(String key) {
        try {
            storage.delete(key);
        } catch (RuntimeException e) {
            // The database remains authoritative. A failed draft cleanup leaves a private orphan, never a missing
            // submitted proof; log only the opaque key and exception type so no uploaded data is exposed.
            LOGGER.error("Could not clean up private proof object {} ({}).", key, e.getClass().getSimpleName());
        }
    }

    private static List<ProofUpload> safeUploads(List<ProofUpload> uploads) {
        return uploads == null ? List.of() : List.copyOf(uploads);
    }

    private static final class StorageChanges {
        private final List<String> deleteAfterCommit = new ArrayList<>();
        private final List<String> deleteAfterRollback = new ArrayList<>();
    }
}
