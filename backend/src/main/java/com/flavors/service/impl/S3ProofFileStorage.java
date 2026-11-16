package com.flavors.service.impl;

import com.flavors.exception.ProofStorageException;
import com.flavors.service.ProofFileContent;
import com.flavors.service.ProofFileStorage;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/** Railway Bucket adapter. Railway supplies the endpoint and credentials through reference variables. */
public final class S3ProofFileStorage implements ProofFileStorage {

    private final S3Client client;
    private final String bucket;

    public S3ProofFileStorage(S3Client client, String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    @Override
    public void store(String key, byte[] bytes, String contentType) {
        try {
            client.putObject(PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType)
                    .contentLength((long) bytes.length)
                    // Submitted evidence is append-only. Even an impossible UUID collision must not overwrite it.
                    .ifNoneMatch("*")
                    .build(), RequestBody.fromBytes(bytes));
        } catch (SdkException e) {
            throw new ProofStorageException("The screenshot could not be stored.", e);
        }
    }

    @Override
    public ProofFileContent read(String key, String contentType) {
        try {
            ResponseBytes<GetObjectResponse> object = client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(bucket).key(key).build());
            return new ProofFileContent(object.asByteArray(), contentType);
        } catch (SdkException e) {
            throw new ProofStorageException("The screenshot could not be read.", e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (SdkException e) {
            throw new ProofStorageException("The draft screenshot could not be removed.", e);
        }
    }
}
