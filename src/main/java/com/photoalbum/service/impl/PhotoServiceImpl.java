package com.photoalbum.service.impl;

import com.photoalbum.model.Photo;
import com.photoalbum.model.UploadResult;
import com.photoalbum.repository.PhotoRepository;
import com.photoalbum.service.PhotoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service implementation for photo operations including upload, retrieval, and deletion
 */
@Service
@Transactional
public class PhotoServiceImpl implements PhotoService {

    private static final Logger logger = LoggerFactory.getLogger(PhotoServiceImpl.class);

    private final PhotoRepository photoRepository;
    private final long maxFileSizeBytes;
    private final List<String> allowedMimeTypes;

    public PhotoServiceImpl(
            PhotoRepository photoRepository,
            @Value("${app.file-upload.max-file-size-bytes}") long maxFileSizeBytes,
            @Value("${app.file-upload.allowed-mime-types}") String[] allowedMimeTypes) {
        this.photoRepository = photoRepository;
        this.maxFileSizeBytes = maxFileSizeBytes;
        this.allowedMimeTypes = Arrays.asList(allowedMimeTypes);
    }

    /**
     * Get all photos ordered by upload date (newest first)
     */
    @Override
    @Transactional(readOnly = true)
    public List<Photo> getAllPhotos() {
        try {
            return photoRepository.findAllOrderByUploadedAtDesc();
        } catch (Exception ex) {
            logger.error("Error retrieving photos from database", ex);
            throw new RuntimeException("Error retrieving photos", ex);
        }
    }

    /**
     * Get a specific photo by ID
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<Photo> getPhotoById(String id) {
        try {
            return photoRepository.findById(id);
        } catch (Exception ex) {
            logger.error("Error retrieving photo with ID {}", id, ex);
            throw new RuntimeException("Error retrieving photo", ex);
        }
    }

    /**
     * Upload a photo file
     */
    @Override
    public UploadResult uploadPhoto(MultipartFile file) {
        UploadResult result = new UploadResult();
        result.setFileName(file.getOriginalFilename());

        try {
            // Read file bytes first so we can validate by actual content (magic bytes)
            byte[] photoData;
            try {
                photoData = file.getBytes();
            } catch (IOException ex) {
                logger.error("Error reading file data for {}", file.getOriginalFilename(), ex);
                result.setSuccess(false);
                result.setErrorMessage("Error reading file data. Please try again.");
                return result;
            }

            // Validate file length
            if (photoData.length <= 0) {
                result.setSuccess(false);
                result.setErrorMessage("File is empty.");
                return result;
            }

            // Validate file type using magic bytes from actual file content,
            // not the client-supplied Content-Type header (which can be spoofed)
            String detectedMimeType = detectMimeTypeFromBytes(photoData);
            if (detectedMimeType == null || !allowedMimeTypes.contains(detectedMimeType)) {
                result.setSuccess(false);
                result.setErrorMessage("File type not supported. Please upload JPEG, PNG, GIF, or WebP images.");
                logger.warn("Upload rejected: Unrecognized file signature for {}, detected type: {}, client-supplied type: {}",
                    file.getOriginalFilename(), detectedMimeType, file.getContentType());
                return result;
            }

            // Validate file size (use photoData.length for consistency with actual read bytes)
            if (photoData.length > maxFileSizeBytes) {
                result.setSuccess(false);
                result.setErrorMessage(String.format("File size exceeds %dMB limit.", maxFileSizeBytes / 1024 / 1024));
                logger.warn("Upload rejected: File size {} exceeds limit for {}", 
                    photoData.length, file.getOriginalFilename());
                return result;
            }

            // Generate unique filename for compatibility (stored in database, not on disk)
            String extension = getFileExtension(file.getOriginalFilename());
            String storedFileName = UUID.randomUUID().toString() + extension;
            String relativePath = "/uploads/" + storedFileName; // For compatibility only

            // Extract image dimensions from byte array
            Integer width = null;
            Integer height = null;
            try (ByteArrayInputStream bis = new ByteArrayInputStream(photoData)) {
                BufferedImage image = ImageIO.read(bis);
                if (image != null) {
                    width = image.getWidth();
                    height = image.getHeight();
                }
            } catch (Exception ex) {
                logger.warn("Could not extract image dimensions for {}", file.getOriginalFilename(), ex);
                // Continue without dimensions - not critical
            }

            // Create photo entity with database BLOB storage
            Photo photo = new Photo(
                file.getOriginalFilename(),
                photoData,  // Store actual photo data in Oracle database
                storedFileName,
                relativePath, // Keep for compatibility, not used for serving
                file.getSize(),
                detectedMimeType  // Use server-detected MIME type, not client-supplied header
            );
            photo.setWidth(width);
            photo.setHeight(height);

            // Save to database (with BLOB photo data)
            try {
                photo = photoRepository.save(photo);

                result.setSuccess(true);
                result.setPhotoId(photo.getId());

                logger.info("Successfully uploaded photo {} with ID {} to Oracle database", 
                    file.getOriginalFilename(), photo.getId());
            } catch (Exception ex) {
                logger.error("Error saving photo to Oracle database for {}", file.getOriginalFilename(), ex);
                result.setSuccess(false);
                result.setErrorMessage("Error saving photo to database. Please try again.");
            }
        } catch (Exception ex) {
            logger.error("Unexpected error during photo upload for {}", file.getOriginalFilename(), ex);
            result.setSuccess(false);
            result.setErrorMessage("An unexpected error occurred. Please try again.");
        }

        return result;
    }

    /**
     * Delete a photo by ID
     */
    @Override
    public boolean deletePhoto(String id) {
        try {
            Optional<Photo> photoOpt = photoRepository.findById(id);
            if (!photoOpt.isPresent()) {
                logger.warn("Photo with ID {} not found for deletion", id);
                return false;
            }

            Photo photo = photoOpt.get();

            // Delete from Oracle database (photos stored as BLOB)
            photoRepository.delete(photo);

            logger.info("Successfully deleted photo ID {} from Oracle database", id);
            return true;
        } catch (Exception ex) {
            logger.error("Error deleting photo with ID {} from Oracle database", id, ex);
            throw new RuntimeException("Error deleting photo", ex);
        }
    }

    /**
     * Get the previous photo (older) for navigation
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<Photo> getPreviousPhoto(Photo currentPhoto) {
        List<Photo> olderPhotos = photoRepository.findPhotosUploadedBefore(currentPhoto.getUploadedAt());
        return olderPhotos.isEmpty() ? Optional.<Photo>empty() : Optional.of(olderPhotos.get(0));
    }

    /**
     * Get the next photo (newer) for navigation
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<Photo> getNextPhoto(Photo currentPhoto) {
        List<Photo> newerPhotos = photoRepository.findPhotosUploadedAfter(currentPhoto.getUploadedAt());
        return newerPhotos.isEmpty() ? Optional.<Photo>empty() : Optional.of(newerPhotos.get(0));
    }

    // Magic byte signatures for supported image formats
    private static final byte[] JPEG_MAGIC  = {(byte)0xFF, (byte)0xD8, (byte)0xFF};
    private static final byte[] PNG_MAGIC   = {(byte)0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] GIF87_MAGIC = {0x47, 0x49, 0x46, 0x38, 0x37, 0x61}; // GIF87a
    private static final byte[] GIF89_MAGIC = {0x47, 0x49, 0x46, 0x38, 0x39, 0x61}; // GIF89a
    private static final byte[] RIFF_MAGIC  = {0x52, 0x49, 0x46, 0x46};             // RIFF (WebP container)
    private static final byte[] WEBP_MARKER = {0x57, 0x45, 0x42, 0x50};             // WEBP

    /**
     * Detect the MIME type of a file by inspecting its magic bytes (file signature).
     * This provides server-side validation independent of the client-supplied Content-Type header.
     *
     * @param bytes the raw file bytes
     * @return the detected MIME type, or {@code null} if the signature is unrecognised
     */
    private String detectMimeTypeFromBytes(byte[] bytes) {
        if (bytes == null || bytes.length < 12) {
            return null;
        }
        if (startsWith(bytes, JPEG_MAGIC)) {
            return "image/jpeg";
        }
        if (startsWith(bytes, PNG_MAGIC)) {
            return "image/png";
        }
        if (startsWith(bytes, GIF87_MAGIC) || startsWith(bytes, GIF89_MAGIC)) {
            return "image/gif";
        }
        // WebP: bytes 0-3 == "RIFF", bytes 8-11 == "WEBP"
        if (bytes.length >= 12 && startsWith(bytes, RIFF_MAGIC)
                && bytes[8] == WEBP_MARKER[0] && bytes[9] == WEBP_MARKER[1]
                && bytes[10] == WEBP_MARKER[2] && bytes[11] == WEBP_MARKER[3]) {
            return "image/webp";
        }
        return null;
    }

    /**
     * Returns {@code true} if {@code data} starts with all bytes in {@code prefix}.
     */
    private boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Extract file extension from filename
     */
    private String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "";
        }
        int lastDotIndex = filename.lastIndexOf('.');
        return lastDotIndex > 0 ? filename.substring(lastDotIndex) : "";
    }
}