package com.example.AI.Doc.Assistant.service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class FileStorageService {

    @Value("${app.file.upload-dir}")
    private String uploadDir;

    public Path save(MultipartFile file) throws IOException {

        Path dir = Paths.get(uploadDir);

        if(!Files.exists(dir))
            Files.createDirectories(dir);

        Path path = dir.resolve(file.getOriginalFilename());

        Files.copy(file.getInputStream(), path);

        return path;
    }
}
