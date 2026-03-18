package com.photoalbum.controller;

import com.photoalbum.model.PhotoBinaryData;
import com.photoalbum.service.PhotoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Optional;

/**
 * Controller for serving photo files from Oracle database BLOB storage
 */
@Controller
@RequestMapping("/photo")
public class PhotoFileController {

    private static final Logger logger = LoggerFactory.getLogger(PhotoFileController.class);

    private final PhotoService photoService;

    public PhotoFileController(PhotoService photoService) {
        this.photoService = photoService;
    }

    /**
     * Serves a photo file by ID from Oracle database BLOB storage
     */
    @GetMapping("/{id}")
    public ResponseEntity<Resource> servePhoto(@PathVariable String id) {
        if (id == null || id.trim().isEmpty()) {
            logger.warn("Photo file request with null or empty ID");
            return ResponseEntity.notFound().build();
        }

        try {
            logger.info("=== DEBUGGING: Serving photo request for ID {} ===", id);
            Optional<PhotoBinaryData> photoDataOpt = photoService.getPhotoFileData(id);

            if (!photoDataOpt.isPresent()) {
                logger.warn("Photo with ID {} not found or has no data", id);
                return ResponseEntity.notFound().build();
            }

            PhotoBinaryData photoData = photoDataOpt.get();
            byte[] data = photoData.getData();

            logger.info("Found photo: originalFileName={}, mimeType={}",
                    photoData.getOriginalFileName(), photoData.getMimeType());
            logger.info("Photo data retrieved: {} bytes, first 10 bytes: {}",
                    data.length,
                    data.length >= 10 ? java.util.Arrays.toString(java.util.Arrays.copyOf(data, 10)) : "less than 10 bytes");

            Resource resource = new ByteArrayResource(data);

            logger.info("Serving photo ID {} ({}, {} bytes) from Oracle database",
                    id, photoData.getOriginalFileName(), data.length);

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(photoData.getMimeType()))
                    .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate, private")
                    .header(HttpHeaders.PRAGMA, "no-cache")
                    .header(HttpHeaders.EXPIRES, "0")
                    .header("X-Photo-ID", String.valueOf(id))
                    .header("X-Photo-Name", photoData.getOriginalFileName())
                    .header("X-Photo-Size", String.valueOf(data.length))
                    .body(resource);
        } catch (Exception ex) {
            logger.error("Error serving photo with ID {} from Oracle database", id, ex);
            return ResponseEntity.status(500).build();
        }
    }
}