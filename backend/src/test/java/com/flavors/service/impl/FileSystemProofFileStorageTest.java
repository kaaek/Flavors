package com.flavors.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flavors.exception.ProofStorageException;
import com.flavors.service.ProofFileContent;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSystemProofFileStorageTest {

    @TempDir
    Path root;

    @Test
    void storesReadsAndDeletesInsideItsRoot() throws Exception {
        FileSystemProofFileStorage storage = new FileSystemProofFileStorage(root);

        storage.store("proof/42/5/image.png", new byte[] {1, 2, 3}, "image/png");
        ProofFileContent read = storage.read("proof/42/5/image.png", "image/png");

        assertThat(read.bytes()).containsExactly(1, 2, 3);
        assertThat(read.contentType()).isEqualTo("image/png");
        assertThat(Files.exists(root.resolve("proof/42/5/image.png"))).isTrue();

        storage.delete("proof/42/5/image.png");
        assertThat(Files.exists(root.resolve("proof/42/5/image.png"))).isFalse();
    }

    @Test
    void refusesTraversalAndNeverOverwritesAnExistingObject() {
        FileSystemProofFileStorage storage = new FileSystemProofFileStorage(root);

        assertThatThrownBy(() -> storage.store("../outside.png", new byte[] {1}, "image/png"))
                .isInstanceOf(ProofStorageException.class).hasMessageContaining("Invalid");

        storage.store("proof/same.png", new byte[] {1}, "image/png");
        assertThatThrownBy(() -> storage.store("proof/same.png", new byte[] {2}, "image/png"))
                .isInstanceOf(ProofStorageException.class);
        assertThat(storage.read("proof/same.png", "image/png").bytes()).containsExactly(1);
    }
}
