package com.photoalbum.service.impl;

import com.photoalbum.model.Photo;
import com.photoalbum.model.UploadResult;
import com.photoalbum.repository.PhotoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for file upload validation in PhotoServiceImpl,
 * focused on CWE-434 magic bytes validation.
 */
@ExtendWith(MockitoExtension.class)
class PhotoServiceImplUploadTest {

    @Mock
    private PhotoRepository photoRepository;

    @Mock
    private MultipartFile mockFile;

    private PhotoServiceImpl photoService;

    // Minimal magic byte sequences for each supported image type
    private static final byte[] JPEG_MAGIC = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0};
    private static final byte[] PNG_MAGIC = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    private static final byte[] GIF87A_MAGIC = new byte[]{'G', 'I', 'F', '8', '7', 'a', 0, 0, 0, 0};
    private static final byte[] GIF89A_MAGIC = new byte[]{'G', 'I', 'F', '8', '9', 'a', 0, 0, 0, 0};
    private static final byte[] WEBP_MAGIC = new byte[]{
            'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 0, 0, 0, 0};

    // Content that does not match any supported image signature
    private static final byte[] PHP_SHELL_BYTES = "<?php echo shell_exec($_GET['cmd']); ?>".getBytes();
    private static final byte[] PLAIN_TEXT_BYTES = "Hello, this is not an image!".getBytes();
    private static final byte[] HTML_BYTES = "<html><body>XSS</body></html>".getBytes();

    @BeforeEach
    void setUp() {
        photoService = new PhotoServiceImpl(
                photoRepository,
                10485760L, // 10 MB
                new String[]{"image/jpeg", "image/png", "image/gif", "image/webp"}
        );
    }

    // --- Tests: valid magic bytes should be accepted ---

    @Test
    void uploadPhoto_acceptsJpegByMagicBytes() throws IOException {
        setupMockFile("photo.jpg", JPEG_MAGIC);
        when(photoRepository.save(any(Photo.class))).thenAnswer(i -> i.getArguments()[0]);

        UploadResult result = photoService.uploadPhoto(mockFile);

        assertTrue(result.isSuccess(), "JPEG file with valid magic bytes should be accepted");
    }

    @Test
    void uploadPhoto_acceptsPngByMagicBytes() throws IOException {
        setupMockFile("photo.png", PNG_MAGIC);
        when(photoRepository.save(any(Photo.class))).thenAnswer(i -> i.getArguments()[0]);

        UploadResult result = photoService.uploadPhoto(mockFile);

        assertTrue(result.isSuccess(), "PNG file with valid magic bytes should be accepted");
    }

    @Test
    void uploadPhoto_acceptsGif87aByMagicBytes() throws IOException {
        setupMockFile("photo.gif", GIF87A_MAGIC);
        when(photoRepository.save(any(Photo.class))).thenAnswer(i -> i.getArguments()[0]);

        UploadResult result = photoService.uploadPhoto(mockFile);

        assertTrue(result.isSuccess(), "GIF87a file with valid magic bytes should be accepted");
    }

    @Test
    void uploadPhoto_acceptsGif89aByMagicBytes() throws IOException {
        setupMockFile("photo.gif", GIF89A_MAGIC);
        when(photoRepository.save(any(Photo.class))).thenAnswer(i -> i.getArguments()[0]);

        UploadResult result = photoService.uploadPhoto(mockFile);

        assertTrue(result.isSuccess(), "GIF89a file with valid magic bytes should be accepted");
    }

    @Test
    void uploadPhoto_acceptsWebpByMagicBytes() throws IOException {
        setupMockFile("photo.webp", WEBP_MAGIC);
        when(photoRepository.save(any(Photo.class))).thenAnswer(i -> i.getArguments()[0]);

        UploadResult result = photoService.uploadPhoto(mockFile);

        assertTrue(result.isSuccess(), "WebP file with valid magic bytes should be accepted");
    }

    // --- Tests: spoofed Content-Type with non-image content should be rejected ---

    @Test
    void uploadPhoto_rejectsPhpShellWithSpoofedJpegContentType() throws IOException {
        setupMockFile("shell.jpg", PHP_SHELL_BYTES);

        UploadResult result = photoService.uploadPhoto(mockFile);

        assertFalse(result.isSuccess(), "PHP shell with spoofed image/jpeg Content-Type must be rejected");
        assertTrue(result.getErrorMessage().contains("File type not supported"),
                "Error message should indicate unsupported file type");
        verify(photoRepository, never()).save(any());
    }

    @Test
    void uploadPhoto_rejectsHtmlWithSpoofedPngContentType() throws IOException {
        setupMockFile("malicious.png", HTML_BYTES);

        UploadResult result = photoService.uploadPhoto(mockFile);

        assertFalse(result.isSuccess(), "HTML content with spoofed image/png Content-Type must be rejected");
        verify(photoRepository, never()).save(any());
    }

    @Test
    void uploadPhoto_rejectsPlainTextWithSpoofedGifContentType() throws IOException {
        setupMockFile("fake.gif", PLAIN_TEXT_BYTES);

        UploadResult result = photoService.uploadPhoto(mockFile);

        assertFalse(result.isSuccess(), "Plain text with spoofed image/gif Content-Type must be rejected");
        verify(photoRepository, never()).save(any());
    }

    @Test
    void uploadPhoto_rejectsTooSmallContent() throws IOException {
        setupMockFile("tiny.jpg", new byte[]{(byte) 0xFF, (byte) 0xD8});

        UploadResult result = photoService.uploadPhoto(mockFile);

        assertFalse(result.isSuccess(), "File with fewer than 4 bytes should be rejected by magic bytes check");
        verify(photoRepository, never()).save(any());
    }

    // --- Helper ---

    private void setupMockFile(String filename, byte[] content) throws IOException {
        when(mockFile.getOriginalFilename()).thenReturn(filename);
        when(mockFile.getSize()).thenReturn((long) content.length);
        when(mockFile.getBytes()).thenReturn(content);
    }
}
