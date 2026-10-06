package com.pmis.docket.server.service;

import com.pmis.docket.server.config.DocketProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Stores file contents on the server's disk.
 * Each upload becomes one blob: storage-root/blobs/ab/cd/<uuid>.
 * Folder structure and names live in the database, so renames and moves never touch the disk,
 * and old versions can be kept side by side.
 */
@Service
public class StorageService {
    private final Path root;

    public StorageService(DocketProperties props) {
        this.root = Path.of(props.getStorageRoot()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root.resolve("blobs"));
            Files.createDirectories(root.resolve("tmp"));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create storage folder " + root, e);
        }
    }

    public Path root() { return root; }

    public Stored store(InputStream in) {
        String key = UUID.randomUUID().toString().replace("-", "");
        Path target = pathFor(key);
        Path tmp = root.resolve("tmp").resolve(key + ".part");
        try {
            Files.createDirectories(target.getParent());
            long size = Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
            return new Stored(key, size);
        } catch (IOException e) {
            try { Files.deleteIfExists(tmp); } catch (IOException ignored) { }
            throw new UncheckedIOException("Could not save the file on the server.", e);
        }
    }

    public Stored store(byte[] bytes) {
        return store(new java.io.ByteArrayInputStream(bytes));
    }

    public Path pathFor(String key) {
        return root.resolve("blobs").resolve(key.substring(0, 2)).resolve(key.substring(2, 4)).resolve(key);
    }

    public long freeBytes() {
        try { return Files.getFileStore(root).getUsableSpace(); } catch (IOException e) { return -1; }
    }

    public long totalBytes() {
        try { return Files.getFileStore(root).getTotalSpace(); } catch (IOException e) { return -1; }
    }

    public record Stored(String key, long size) { }
}
