package com.example.AI.Doc.Assistant.controller;

import com.example.AI.Doc.Assistant.service.DocumentIngestionService;
import com.example.AI.Doc.Assistant.service.FileStorageService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;

@RestController
@RequestMapping("/documents")
public class DocumentController {

    private final DocumentIngestionService ingestionService;
    private final FileStorageService storageService;

    public DocumentController(DocumentIngestionService ingestionService,
                              FileStorageService storageService) {

        this.ingestionService = ingestionService;
        this.storageService = storageService;
    }

    @PostMapping("/upload")
    public String upload(@RequestParam MultipartFile file) throws Exception {

        Path path = storageService.save(file);

        ingestionService.ingest(path);

        return "Document processed";
    }
}
