package com.smart.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import com.smart.exception.InvalidUploadException;

class FileStorageServiceTest {

    private static final byte[] JPEG = pad(0xFF, 0xD8, 0xFF, 0xE0);
    private static final byte[] PNG = pad(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A);
    private static final byte[] GIF = pad('G', 'I', 'F', '8', '9', 'a');
    private static final byte[] WEBP = pad('R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P');

    @TempDir
    Path dir;

    private FileStorageService service;

    @BeforeEach
    void setUp() {
        service = new FileStorageService(dir.toString(), 2 * 1024 * 1024);
    }

    private static byte[] pad(int... values) {
        byte[] bytes = new byte[64];
        for (int i = 0; i < values.length; i++) {
            bytes[i] = (byte) values[i];
        }
        return bytes;
    }

    private static MockMultipartFile upload(String clientName, String claimedType, byte[] content) {
        return new MockMultipartFile("profileImage", clientName, claimedType, content);
    }

    private long fileCount() throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.count();
        }
    }

    @Test
    void storesSupportedImagesUnderRandomNamesWithTheRightExtension() throws IOException {
        assertTrue(service.store(upload("a.jpg", "image/jpeg", JPEG)).endsWith(".jpg"));
        assertTrue(service.store(upload("a.png", "image/png", PNG)).endsWith(".png"));
        assertTrue(service.store(upload("a.gif", "image/gif", GIF)).endsWith(".gif"));
        assertTrue(service.store(upload("a.webp", "image/webp", WEBP)).endsWith(".webp"));
        assertEquals(4, fileCount());
    }

    @Test
    void theClientSuppliedNameIsNeverUsed() throws IOException {
        String stored = service.store(upload("../../etc/evil.jsp", "image/png", PNG));

        assertTrue(stored.matches("[0-9a-f]{32}\\.png"), stored);
        assertArrayEquals(PNG, Files.readAllBytes(dir.resolve(stored)));
    }

    @Test
    void theTypeIsDecidedByContentNotByNameOrDeclaredType() throws IOException {
        byte[] script = "<?php echo 1; ?>".getBytes(StandardCharsets.UTF_8);

        assertThrows(InvalidUploadException.class, () -> service.store(upload("photo.jpg", "image/jpeg", script)));
        assertEquals(0, fileCount());
    }

    @Test
    void svgIsRefusedBecauseItCanCarryScript() {
        byte[] svg = "<svg xmlns='http://www.w3.org/2000/svg' onload='alert(1)'/>".getBytes(StandardCharsets.UTF_8);
        assertThrows(InvalidUploadException.class, () -> service.store(upload("a.svg", "image/svg+xml", svg)));
    }

    @Test
    void rejectsEmptyAndMissingFiles() {
        assertThrows(InvalidUploadException.class, () -> service.store(upload("a.png", "image/png", new byte[0])));
        assertThrows(InvalidUploadException.class, () -> service.store(null));
    }

    @Test
    void rejectsFilesOverTheSizeLimitAndLeavesNothingBehind() throws IOException {
        byte[] big = new byte[2 * 1024 * 1024 + 1];
        System.arraycopy(JPEG, 0, big, 0, 4);

        InvalidUploadException ex = assertThrows(InvalidUploadException.class,
                () -> service.store(upload("big.jpg", "image/jpeg", big)));

        assertTrue(ex.getMessage().contains("2 MB"));
        assertEquals(0, fileCount());
    }

    @Test
    void deleteRemovesTheStoredFile() throws IOException {
        String stored = service.store(upload("a.png", "image/png", PNG));
        service.delete(stored);
        assertFalse(Files.exists(dir.resolve(stored)));
    }

    @Test
    void deleteIgnoresPlaceholdersTraversalAndNull() throws IOException {
        Path outside = Files.createTempFile("outside", ".jpg");
        try {
            service.delete("../" + outside.getFileName());
            service.delete("..\\x");
            service.delete("/etc/passwd");
            service.delete("default.png");
            service.delete("contact.png");
            service.delete("");
            service.delete(null);
            assertTrue(Files.exists(outside));
        } finally {
            Files.deleteIfExists(outside);
        }
    }
}
