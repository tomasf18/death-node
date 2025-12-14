package com.deathnode.server.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

@Service
public class FileStorageService {

    private final Path basePath;

    public FileStorageService(@Value("${storage.envelopes-path}") String baseDir) throws IOException {
        this.basePath = Path.of(baseDir);
        Files.createDirectories(this.basePath); // auto-create if missing
    }

    public Path store(byte[] data, String filename) throws IOException {
        Path target = basePath.resolve(filename);
        Files.write(target, data, StandardOpenOption.CREATE_NEW);
        return target;
    }

    public byte[] read(String filename) throws IOException {
        Path target = basePath.resolve(filename);
        return Files.readAllBytes(target);
    }

    public boolean exists(String filename) {
        return Files.exists(basePath.resolve(filename));
    }

    public void delete(String filename) throws IOException {
        Files.deleteIfExists(basePath.resolve(filename));
    }

    public Path getFullPath(String filename) {
        return basePath.resolve(filename);
    }
}
