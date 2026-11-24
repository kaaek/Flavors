package com.flavors.config;

import com.flavors.service.ProofFileStorage;
import com.flavors.service.impl.FileSystemProofFileStorage;
import com.flavors.service.impl.S3ProofFileStorage;
import java.net.URI;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/** Selects local storage in dev/tests and a private Railway Bucket everywhere else. */
@Configuration(proxyBeanMethods = false)
public class ProofStorageConfig {

    @Bean
    @ConditionalOnProperty(name = "flavors.proof-storage.type", havingValue = "filesystem")
    ProofFileStorage fileSystemProofStorage(
            @Value("${flavors.proof-storage.filesystem-root}") Path root) {
        return new FileSystemProofFileStorage(root);
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "flavors.proof-storage.type", havingValue = "s3", matchIfMissing = true)
    S3Client proofS3Client(
            @Value("${flavors.proof-storage.endpoint}") String endpoint,
            @Value("${flavors.proof-storage.region}") String region,
            @Value("${flavors.proof-storage.access-key}") String accessKey,
            @Value("${flavors.proof-storage.secret-key}") String secretKey) {
        requireValue(endpoint, "FLAVORS_PROOF_STORAGE_ENDPOINT");
        requireValue(region, "FLAVORS_PROOF_STORAGE_REGION");
        requireValue(accessKey, "FLAVORS_PROOF_STORAGE_ACCESS_KEY");
        requireValue(secretKey, "FLAVORS_PROOF_STORAGE_SECRET_KEY");
        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "flavors.proof-storage.type", havingValue = "s3", matchIfMissing = true)
    ProofFileStorage s3ProofStorage(S3Client proofS3Client,
            @Value("${flavors.proof-storage.bucket}") String bucket) {
        requireValue(bucket, "FLAVORS_PROOF_STORAGE_BUCKET");
        return new S3ProofFileStorage(proofS3Client, bucket);
    }

    private static void requireValue(String value, String variable) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(variable + " is required when delivery-proof storage uses S3.");
        }
    }
}
