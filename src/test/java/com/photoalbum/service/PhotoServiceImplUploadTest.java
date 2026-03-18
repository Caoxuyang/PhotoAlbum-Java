package com.photoalbum.service;

import com.photoalbum.model.Photo;
import com.photoalbum.model.UploadResult;
import com.photoalbum.repository.PhotoRepository;
import com.photoalbum.service.impl.PhotoServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for file-upload validation in {@link PhotoServiceImpl}.
 */
@ExtendWith(MockitoExtension.class)
class PhotoServiceImplUploadTest {

    @Mock
    private PhotoRepository photoRepository;

    private PhotoServiceImpl service;

    // Minimal valid JPEG bytes: FF D8 FF E0 + 8 padding bytes
    private static final byte[] JPEG_MAGIC = {
        (byte)0xFF, (byte)0xD8, (byte)0xFF, (byte)0xE0,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
    };

    // Minimal valid PNG bytes: 89 50 4E 47 0D 0A 1A 0A + 4 padding bytes
    private static final byte[] PNG_MAGIC = {
        (byte)0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        0x00, 0x00, 0x00, 0x00
    };

    // Minimal valid GIF bytes: GIF89a + 6 padding bytes
    private static final byte[] GIF_MAGIC = {
        0x47, 0x49, 0x46, 0x38, 0x39, 0x61,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00
    };

    // Minimal valid WebP bytes: RIFF + 4 size bytes + WEBP
    private static final byte[] WEBP_MAGIC = {
        0x52, 0x49, 0x46, 0x46,  // RIFF
        0x00, 0x00, 0x00, 0x00,  // file size (ignored for test)
        0x57, 0x45, 0x42, 0x50   // WEBP
    };

    @BeforeEach
    void setUp() {
        service = new PhotoServiceImpl(
                photoRepository,
                10_485_760L, // 10 MB
                new String[]{"image/jpeg", "image/png", "image/gif", "image/webp"});
    }

    // -------------------------------------------------------------------------
    // isValidImageMagicBytes – unit-level tests
    // -------------------------------------------------------------------------

    @Test
    void magicBytes_jpeg_accepted() {
        assertTrue(PhotoServiceImpl.isValidImageMagicBytes(JPEG_MAGIC));
    }

    @Test
    void magicBytes_png_accepted() {
        assertTrue(PhotoServiceImpl.isValidImageMagicBytes(PNG_MAGIC));
    }

    @Test
    void magicBytes_gif_accepted() {
        assertTrue(PhotoServiceImpl.isValidImageMagicBytes(GIF_MAGIC));
    }

    @Test
    void magicBytes_webp_accepted() {
        assertTrue(PhotoServiceImpl.isValidImageMagicBytes(WEBP_MAGIC));
    }

    @Test
    void magicBytes_null_rejected() {
        assertFalse(PhotoServiceImpl.isValidImageMagicBytes(null));
    }

    @Test
    void magicBytes_tooShort_rejected() {
        assertFalse(PhotoServiceImpl.isValidImageMagicBytes(new byte[]{0x00, 0x01}));
    }

    @Test
    void magicBytes_randomData_rejected() {
        assertFalse(PhotoServiceImpl.isValidImageMagicBytes(new byte[12])); // all zeros
    }

    // -------------------------------------------------------------------------
    // uploadPhoto – validation tests (no database interaction required for most)
    // -------------------------------------------------------------------------

    @Test
    void upload_nullContentType_rejected() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", null, JPEG_MAGIC);
        UploadResult result = service.uploadPhoto(file);
        assertFalse(result.isSuccess());
        assertNotNull(result.getErrorMessage());
    }

    @Test
    void upload_disallowedMimeType_rejected() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "script.jpg", "application/octet-stream", JPEG_MAGIC);
        UploadResult result = service.uploadPhoto(file);
        assertFalse(result.isSuccess());
    }

    @Test
    void upload_disallowedExtension_rejected() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "malware.exe", "image/jpeg", JPEG_MAGIC);
        UploadResult result = service.uploadPhoto(file);
        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("extension"));
    }

    @Test
    void upload_noExtension_rejected() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "noextension", "image/jpeg", JPEG_MAGIC);
        UploadResult result = service.uploadPhoto(file);
        assertFalse(result.isSuccess());
    }

    @Test
    void upload_mimeTypeSpoofed_rejected() {
        // File claims to be JPEG but magic bytes are all zeros (not a valid image)
        byte[] spoofedData = new byte[20]; // all zeros
        MockMultipartFile file = new MockMultipartFile(
                "file", "evil.jpg", "image/jpeg", spoofedData);
        UploadResult result = service.uploadPhoto(file);
        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("content"));
    }

    @Test
    void upload_emptyFile_rejected() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.jpg", "image/jpeg", new byte[0]);
        UploadResult result = service.uploadPhoto(file);
        assertFalse(result.isSuccess());
    }

    @Test
    void upload_fileTooLarge_rejected() {
        // Create a byte array just over 10 MB with valid JPEG magic
        byte[] oversized = new byte[10_485_761];
        System.arraycopy(JPEG_MAGIC, 0, oversized, 0, JPEG_MAGIC.length);
        MockMultipartFile file = new MockMultipartFile(
                "file", "huge.jpg", "image/jpeg", oversized);
        UploadResult result = service.uploadPhoto(file);
        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("exceeds"));
    }

    @Test
    void upload_validWebp_savedSuccessfully() {
        Photo saved = new Photo();
        saved.setId("test-id-123");
        when(photoRepository.save(any(Photo.class))).thenReturn(saved);

        MockMultipartFile file = new MockMultipartFile(
                "file", "image.webp", "image/webp", WEBP_MAGIC);
        UploadResult result = service.uploadPhoto(file);
        assertTrue(result.isSuccess());
        assertEquals("test-id-123", result.getPhotoId());
    }

    @Test
    void upload_extensionCaseInsensitive_accepted() {
        // .JPG uppercase should be treated the same as .jpg
        Photo saved = new Photo();
        saved.setId("test-id-456");
        when(photoRepository.save(any(Photo.class))).thenReturn(saved);

        MockMultipartFile file = new MockMultipartFile(
                "file", "PHOTO.JPG", "image/jpeg", JPEG_MAGIC);
        UploadResult result = service.uploadPhoto(file);
        assertTrue(result.isSuccess());
    }
}
