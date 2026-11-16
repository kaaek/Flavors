package com.flavors.service.impl;

import com.flavors.exception.ProofStorageException;
import com.flavors.service.ProofFileContent;
import com.flavors.service.ProofFileStorage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Local-development storage. Production selects {@link S3ProofFileStorage} instead. */
public final class FileSystemProofFileStorage implements ProofFileStorage {

    private final Path root;

    public FileSystemProofFileStorage(Path root) {
        this.root = root.toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.root);
        } catch (IOException e) {
            throw new ProofStorageException("Delivery-proof storage is not available.", e);
        }
    }

    @Override
    public void store(String key, byte[] bytes, String contentType) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            // CREATE_NEW is the portable no-overwrite guarantee. Keys are random and the transaction
            // cleanup removes this object if the accompanying database row cannot commit.
            Files.write(target, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new ProofStorageException("The screenshot could not be stored.", e);
        }
    }

    @Override
    public ProofFileContent read(String key, String contentType) {
        try {
            return new ProofFileContent(Files.readAllBytes(resolve(key)), contentType);
        } catch (IOException e) {
            throw new ProofStorageException("The screenshot could not be read.", e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new ProofStorageException("The draft screenshot could not be removed.", e);
        }
    }

    private Path resolve(String key) {
        Path resolved = root.resolve(key).normalize();
        if (!resolved.startsWith(root) || resolved.equals(root)) {
            throw new ProofStorageException("Invalid delivery-proof storage key.");
        }
        return resolved;
    }
}
