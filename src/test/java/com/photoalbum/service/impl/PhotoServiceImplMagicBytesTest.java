package com.photoalbum.service.impl;

import com.photoalbum.model.UploadResult;
import com.photoalbum.repository.PhotoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for CWE-434 fix: magic byte validation in PhotoServiceImpl.
 */
class PhotoServiceImplMagicBytesTest {

    private PhotoServiceImpl service;

    // Minimal valid magic-byte headers for each supported type
    private static final byte[] JPEG_BYTES = new byte[]{
        (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 0, 0, 0, 0
    };
    private static final byte[] PNG_BYTES = new byte[]{
        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0
    };
    private static final byte[] GIF87_BYTES = new byte[]{
        0x47, 0x49, 0x46, 0x38, 0x37, 0x61, 0, 0, 0, 0, 0, 0
    };
    private static final byte[] GIF89_BYTES = new byte[]{
        0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0, 0, 0, 0, 0, 0
    };
    private static final byte[] WEBP_BYTES = new byte[]{
        0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50
    };
    private static final byte[] FAKE_JPEG_BYTES = new byte[]{
        0x3C, 0x73, 0x63, 0x72, 0x69, 0x70, 0x74, 0x3E, 0, 0, 0, 0 // "<script>"
    };

    @BeforeEach
    void setUp() {
        PhotoRepository repo = mock(PhotoRepository.class);
        String[] allowedTypes = {"image/jpeg", "image/png", "image/gif", "image/webp"};
        service = new PhotoServiceImpl(repo, 10_485_760L, allowedTypes);
    }

    // ---- isValidImageContent unit tests ----

    @Test
    void jpeg_validMagicBytes_returnsTrue() {
        assertTrue(service.isValidImageContent(JPEG_BYTES, "image/jpeg"));
    }

    @Test
    void jpeg_wrongMagicBytes_returnsFalse() {
        assertFalse(service.isValidImageContent(FAKE_JPEG_BYTES, "image/jpeg"));
    }

    @Test
    void png_validMagicBytes_returnsTrue() {
        assertTrue(service.isValidImageContent(PNG_BYTES, "image/png"));
    }

    @Test
    void png_wrongMagicBytes_returnsFalse() {
        assertFalse(service.isValidImageContent(FAKE_JPEG_BYTES, "image/png"));
    }

    @Test
    void gif87_validMagicBytes_returnsTrue() {
        assertTrue(service.isValidImageContent(GIF87_BYTES, "image/gif"));
    }

    @Test
    void gif89_validMagicBytes_returnsTrue() {
        assertTrue(service.isValidImageContent(GIF89_BYTES, "image/gif"));
    }

    @Test
    void gif_wrongMagicBytes_returnsFalse() {
        assertFalse(service.isValidImageContent(FAKE_JPEG_BYTES, "image/gif"));
    }

    @Test
    void webp_validMagicBytes_returnsTrue() {
        assertTrue(service.isValidImageContent(WEBP_BYTES, "image/webp"));
    }

    @Test
    void webp_wrongMagicBytes_returnsFalse() {
        assertFalse(service.isValidImageContent(FAKE_JPEG_BYTES, "image/webp"));
    }

    @Test
    void nullData_returnsFalse() {
        assertFalse(service.isValidImageContent(null, "image/jpeg"));
    }

    @Test
    void tooShortData_returnsFalse() {
        assertFalse(service.isValidImageContent(new byte[]{(byte) 0xFF, (byte) 0xD8}, "image/jpeg"));
    }

    @Test
    void nullMimeType_returnsFalse() {
        assertFalse(service.isValidImageContent(JPEG_BYTES, null));
    }

    @Test
    void unknownMimeType_returnsFalse() {
        assertFalse(service.isValidImageContent(JPEG_BYTES, "application/octet-stream"));
    }

    // ---- uploadPhoto integration: spoofed Content-Type is rejected ----

    @Test
    void uploadPhoto_spoofedJpegContentType_isRejected() {
        // A file with "<script>" bytes but claiming to be image/jpeg
        MockMultipartFile malicious = new MockMultipartFile(
            "file", "evil.jpg", "image/jpeg", FAKE_JPEG_BYTES);

        UploadResult result = service.uploadPhoto(malicious);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("does not match"));
    }

    @Test
    void uploadPhoto_validJpeg_passesContentValidation() {
        // Valid JPEG magic bytes – will fail at DB save (mocked repo returns null),
        // but must NOT fail at the magic-bytes check step.
        MockMultipartFile file = new MockMultipartFile(
            "file", "photo.jpg", "image/jpeg", JPEG_BYTES);

        UploadResult result = service.uploadPhoto(file);

        // The DB save will produce an error because the mock returns null,
        // but the error message must not be the magic-byte rejection message.
        assertNotEquals("File content does not match the declared image type. Upload rejected.",
            result.getErrorMessage());
    }
}
