package com.smart.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.smart.exception.InvalidUploadException;

/**
 * Stores profile/contact photos on disk under {@code app.upload-dir} (default {@code user-photos/}).
 * <ul>
 * <li>The file type is decided by the file's first bytes (magic numbers), not by its name or the
 * browser-supplied content type, so a renamed script cannot sneak in. SVG is refused (script risk).</li>
 * <li>The stored name is random, so the client cannot choose a path or overwrite another file.</li>
 * <li>Size is limited by {@code app.upload-max-bytes} (default 2 MB).</li>
 * </ul>
 */
@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    /** Placeholder values older records may contain instead of a real uploaded file. */
    private static final Set<String> NOT_A_FILE = Set.of("", "default.png", "contact.png");

    private final Path root;
    private final long maxBytes;

    public FileStorageService(@Value("${app.upload-dir:user-photos}") String uploadDir,
            @Value("${app.upload-max-bytes:2097152}") long maxBytes) {
        this.root = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.maxBytes = maxBytes;
    }

    public static boolean hasFile(MultipartFile file) {
        return file != null && !file.isEmpty();
    }

    public Path getRoot() {
        return root;
    }

    /** Validates and stores the image, returning the generated file name. */
    public String store(MultipartFile file) {
        if (!hasFile(file)) {
            throw new InvalidUploadException("Please choose an image file.");
        }
        if (file.getSize() > maxBytes) {
            throw new InvalidUploadException("The image is too large. Maximum size is " + describeSize() + ".");
        }
        Path target = null;
        try (InputStream in = file.getInputStream()) {
            byte[] header = new byte[12];
            int read = in.readNBytes(header, 0, header.length);
            String extension = detectExtension(header, read);
            if (extension == null) {
                throw new InvalidUploadException("Only JPG, PNG, GIF or WebP images are allowed.");
            }

            Files.createDirectories(root);
            String name = UUID.randomUUID().toString().replace("-", "") + "." + extension;
            target = root.resolve(name).normalize();
            if (!target.startsWith(root)) {
                throw new InvalidUploadException("Invalid file name.");
            }
            try (OutputStream out = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)) {
                out.write(header, 0, read);
                in.transferTo(out);
            }
            if (Files.size(target) > maxBytes) {
                Files.deleteIfExists(target);
                throw new InvalidUploadException("The image is too large. Maximum size is " + describeSize() + ".");
            }
            return name;
        } catch (IOException e) {
            log.error("Could not store uploaded image", e);
            deleteQuietly(target);
            throw new InvalidUploadException("The image could not be saved. Please try again.");
        }
    }

    /** Deletes a previously stored photo. Placeholder names and anything outside the upload folder are ignored. */
    public void delete(String filename) {
        if (filename == null || NOT_A_FILE.contains(filename.trim()) || filename.contains("/")
                || filename.contains("\\") || filename.contains("..")) {
            return;
        }
        Path file = root.resolve(filename.trim()).normalize();
        if (!file.startsWith(root)) {
            return;
        }
        deleteQuietly(file);
    }

    private void deleteQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("Could not delete file {}", file.getFileName());
        }
    }

    private String describeSize() {
        return maxBytes >= 1024 * 1024 ? (maxBytes / (1024 * 1024)) + " MB" : (maxBytes / 1024) + " KB";
    }

    /** Recognises JPEG, PNG, GIF and WebP by signature; returns null for anything else. */
    static String detectExtension(byte[] h, int len) {
        if (len >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (len >= 8 && (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G'
                && h[4] == 0x0D && h[5] == 0x0A && h[6] == 0x1A && h[7] == 0x0A) {
            return "png";
        }
        if (len >= 6 && h[0] == 'G' && h[1] == 'I' && h[2] == 'F' && h[3] == '8'
                && (h[4] == '7' || h[4] == '9') && h[5] == 'a') {
            return "gif";
        }
        if (len >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P') {
            return "webp";
        }
        return null;
    }
}
