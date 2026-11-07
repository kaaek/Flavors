package com.flavors.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flavors.exception.ProofStorageException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

class S3ProofFileStorageTest {

    @Test
    void writesToTheConfiguredPrivateBucketWithoutAllowingOverwrite() {
        S3Client client = mock(S3Client.class);
        S3ProofFileStorage storage = new S3ProofFileStorage(client, "proof-files-abc123");

        storage.store("proof/1/2/id.png", new byte[] {1, 2, 3}, "image/png");

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(client).putObject(request.capture(), any(RequestBody.class));
        assertThat(request.getValue().bucket()).isEqualTo("proof-files-abc123");
        assertThat(request.getValue().key()).isEqualTo("proof/1/2/id.png");
        assertThat(request.getValue().contentType()).isEqualTo("image/png");
        assertThat(request.getValue().contentLength()).isEqualTo(3);
        assertThat(request.getValue().ifNoneMatch()).isEqualTo("*");
    }

    @Test
    void readsAndDeletesOnlyTheRequestedObject() {
        S3Client client = mock(S3Client.class);
        S3ProofFileStorage storage = new S3ProofFileStorage(client, "proof-files-abc123");
        GetObjectResponse response = GetObjectResponse.builder().contentType("image/png").build();
        when(client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(response, "image".getBytes(StandardCharsets.UTF_8)));

        assertThat(storage.read("proof/1/2/id.png", "image/png").bytes())
                .isEqualTo("image".getBytes(StandardCharsets.UTF_8));
        storage.delete("proof/1/2/id.png");

        ArgumentCaptor<GetObjectRequest> get = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(client).getObjectAsBytes(get.capture());
        assertThat(get.getValue().bucket()).isEqualTo("proof-files-abc123");
        assertThat(get.getValue().key()).isEqualTo("proof/1/2/id.png");
        ArgumentCaptor<DeleteObjectRequest> delete = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(client).deleteObject(delete.capture());
        assertThat(delete.getValue().bucket()).isEqualTo("proof-files-abc123");
        assertThat(delete.getValue().key()).isEqualTo("proof/1/2/id.png");
    }

    @Test
    void hidesProviderFailuresBehindADomainError() {
        S3Client client = mock(S3Client.class);
        S3ProofFileStorage storage = new S3ProofFileStorage(client, "proof-files-abc123");
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(SdkClientException.create("credential detail that must not escape"));

        assertThatThrownBy(() -> storage.store("proof/id.png", new byte[] {1}, "image/png"))
                .isInstanceOf(ProofStorageException.class)
                .hasMessage("The screenshot could not be stored.")
                .hasCauseInstanceOf(SdkClientException.class);
    }
}
