package com.flavors.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/** A published-post link belonging to one delivery-proof version. */
@Entity
public class ProofLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long submissionId;

    @Column(nullable = false, length = 2048)
    private String url;

    /** For JPA only. */
    protected ProofLink() {
    }

    public ProofLink(long submissionId, String url) {
        this.submissionId = submissionId;
        this.url = url;
    }

    public Long getId() {
        return id;
    }

    public Long getSubmissionId() {
        return submissionId;
    }

    public String getUrl() {
        return url;
    }
}
