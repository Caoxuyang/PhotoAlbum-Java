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
            // Validate file type using client-supplied Content-Type as a first pass
            if (file.getContentType() == null || !allowedMimeTypes.contains(file.getContentType().toLowerCase())) {
                result.setSuccess(false);
                result.setErrorMessage("File type not supported. Please upload JPEG, PNG, GIF, or WebP images.");
                logger.warn("Upload rejected: Invalid file type {} for {}", 
                    file.getContentType(), file.getOriginalFilename());
                return result;
            }

            // Validate file size
            if (file.getSize() > maxFileSizeBytes) {
                result.setSuccess(false);
                result.setErrorMessage(String.format("File size exceeds %dMB limit.", maxFileSizeBytes / 1024 / 1024));
                logger.warn("Upload rejected: File size {} exceeds limit for {}", 
                    file.getSize(), file.getOriginalFilename());
                return result;
            }

            // Validate file length
            if (file.getSize() <= 0) {
                result.setSuccess(false);
                result.setErrorMessage("File is empty.");
                logger.warn("Upload rejected: Empty file {}", file.getOriginalFilename());
                return result;
            }

            // Generate unique filename for compatibility (stored in database, not on disk)
            String extension = getFileExtension(file.getOriginalFilename());
            String storedFileName = UUID.randomUUID().toString() + extension;
            String relativePath = "/uploads/" + storedFileName; // For compatibility only

            // Extract image dimensions and read file data
            Integer width = null;
            Integer height = null;
            byte[] photoData = null;
            
            try {
                // Read file content for database storage
                photoData = file.getBytes();

                // CWE-434: Validate actual file content by inspecting magic bytes
                // to prevent a malicious actor from bypassing the Content-Type check
                if (!isAllowedImageContent(photoData)) {
                    result.setSuccess(false);
                    result.setErrorMessage("File content does not match an allowed image format.");
                    logger.warn("Upload rejected: Content-type mismatch / invalid magic bytes for {}", 
                        file.getOriginalFilename());
                    return result;
                }

                // Extract image dimensions from byte array
                try (ByteArrayInputStream bis = new ByteArrayInputStream(photoData)) {
                    BufferedImage image = ImageIO.read(bis);
                    if (image != null) {
                        width = image.getWidth();
                        height = image.getHeight();
                    }
                }
            } catch (IOException ex) {
                logger.error("Error reading file data for {}", file.getOriginalFilename(), ex);
                result.setSuccess(false);
                result.setErrorMessage("Error reading file data. Please try again.");
                return result;
            } catch (Exception ex) {
                logger.warn("Could not extract image dimensions for {}", file.getOriginalFilename(), ex);
                // Continue without dimensions - not critical
            }

            // CWE-665: Guard against a null photoData caused by a swallowed exception
            if (photoData == null) {
                result.setSuccess(false);
                result.setErrorMessage("Error reading file data. Please try again.");
                logger.warn("Upload rejected: photoData is null for {}", file.getOriginalFilename());
                return result;
            }

            // Create photo entity with database BLOB storage
            Photo photo = new Photo(
                file.getOriginalFilename(),
                photoData,  // Store actual photo data in Oracle database
                storedFileName,
                relativePath, // Keep for compatibility, not used for serving
                file.getSize(),
                file.getContentType()
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

    /**
     * CWE-434: Validate the actual file content by checking magic bytes so that
     * a client cannot bypass the MIME-type check by sending a dangerous file with
     * an allowed Content-Type header.
     *
     * Recognised signatures:
     *   JPEG  – FF D8
     *   PNG   – 89 50 4E 47 0D 0A 1A 0A
     *   GIF87a / GIF89a – 47 49 46 38 37 61 / 47 49 46 38 39 61
     *   WebP  – 52 49 46 46 .. .. .. .. 57 45 42 50
     */
    private boolean isAllowedImageContent(byte[] data) {
        if (data == null || data.length < 12) {
            return false;
        }
        // JPEG: starts with FF D8
        if ((data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8) {
            return true;
        }
        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if ((data[0] & 0xFF) == 0x89 && data[1] == 0x50 && data[2] == 0x4E && data[3] == 0x47
                && data[4] == 0x0D && data[5] == 0x0A && data[6] == 0x1A && data[7] == 0x0A) {
            return true;
        }
        // GIF87a or GIF89a: 47 49 46 38 (37|39) 61
        if (data[0] == 0x47 && data[1] == 0x49 && data[2] == 0x46 && data[3] == 0x38
                && (data[4] == 0x37 || data[4] == 0x39) && data[5] == 0x61) {
            return true;
        }
        // WebP: RIFF (4 bytes of size) WEBP
        if (data[0] == 0x52 && data[1] == 0x49 && data[2] == 0x46 && data[3] == 0x46
                && data[8] == 0x57 && data[9] == 0x45 && data[10] == 0x42 && data[11] == 0x50) {
            return true;
        }
        return false;
    }
}