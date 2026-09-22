package com.example.lostfound.controller;

import com.example.lostfound.entity.MediaAsset;
import com.example.lostfound.entity.User;
import com.example.lostfound.service.MediaAssetService;
import com.example.lostfound.service.UserService;
import com.example.lostfound.util.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Handles managed image assets; no client-provided storage URL is trusted. */
@RestController
@RequestMapping("/api")
public class ImageController {

    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/gif", "gif");

    private final MediaAssetService mediaAssetService;
    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final Path uploadRoot;
    private final long maxUploadBytes;
    private final long maxPixels;

    public ImageController(MediaAssetService mediaAssetService,
                           UserService userService,
                           JwtUtil jwtUtil,
                           @Value("${media.upload-dir:uploads/media}") String uploadDir,
                           @Value("${media.max-upload-bytes:5242880}") long maxUploadBytes,
                           @Value("${media.max-pixels:16000000}") long maxPixels) {
        this.mediaAssetService = mediaAssetService;
        this.userService = userService;
        this.jwtUtil = jwtUtil;
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.maxUploadBytes = maxUploadBytes;
        this.maxPixels = maxPixels;
    }

    /**
     * /api/upload remains as a compatibility alias. New clients should call
     * /api/assets and pass a purpose of item or certification.
     */
    @PostMapping({"/assets", "/upload"})
    public ResponseEntity<Map<String, Object>> uploadImage(
            @RequestAttribute("userId") Long userId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "purpose", defaultValue = MediaAssetService.PURPOSE_ITEM) String purpose) {
        try {
            String normalizedPurpose = normalizePurpose(purpose);
            if (file == null || file.isEmpty()) {
                return error(HttpStatus.BAD_REQUEST, "文件不能为空");
            }
            if (file.getSize() > maxUploadBytes) {
                return error(HttpStatus.BAD_REQUEST, "文件不能超过5MB");
            }

            byte[] content = file.getBytes();
            ImageMetadata metadata = inspectImage(content);
            if (metadata.width <= 0 || metadata.height <= 0
                    || (long) metadata.width * metadata.height > maxPixels) {
                return error(HttpStatus.BAD_REQUEST, "图片像素超过限制");
            }

            String extension = EXTENSIONS.get(metadata.contentType);
            String storageKey = normalizedPurpose + "/" + UUID.randomUUID() + "." + extension;
            Path destination = uploadRoot.resolve(storageKey).normalize();
            if (!destination.startsWith(uploadRoot)) {
                return error(HttpStatus.BAD_REQUEST, "图片保存路径无效");
            }
            Files.createDirectories(destination.getParent());
            Files.write(destination, content, StandardOpenOption.CREATE_NEW);

            try {
                MediaAsset asset = new MediaAsset();
                asset.setOwnerId(userId);
                asset.setPurpose(normalizedPurpose);
                asset.setStorageKey(storageKey);
                asset.setOriginalFilename(safeOriginalFilename(file.getOriginalFilename()));
                asset.setContentType(metadata.contentType);
                asset.setSizeBytes((long) content.length);
                asset.setWidth(metadata.width);
                asset.setHeight(metadata.height);
                asset.setIsPublic(MediaAssetService.PURPOSE_ITEM.equals(normalizedPurpose));
                mediaAssetService.save(asset);

                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("assetId", asset.getId());
                response.put("id", asset.getId());
                response.put("url", mediaAssetService.contentUrl(asset.getId()));
                response.put("purpose", asset.getPurpose());
                return ResponseEntity.ok(response);
            } catch (RuntimeException databaseFailure) {
                Files.deleteIfExists(destination);
                throw databaseFailure;
            }
        } catch (IllegalArgumentException e) {
            return error(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IOException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "文件保存失败");
        }
    }

    /**
     * Item assets are public. Certification assets are served only to their
     * owner or an administrator, even though the same endpoint is used.
     */
    @GetMapping("/assets/{assetId}/content")
    public ResponseEntity<?> getContent(@PathVariable Long assetId,
                                        @RequestHeader(value = "Authorization", required = false) String authorization) {
        MediaAsset asset = mediaAssetService.findById(assetId);
        if (asset == null) {
            return error(HttpStatus.NOT_FOUND, "图片资源不存在");
        }

        if (!Boolean.TRUE.equals(asset.getIsPublic())) {
            Long requesterId = optionalUserId(authorization);
            if (requesterId == null) {
                return error(HttpStatus.UNAUTHORIZED, "请先登录");
            }
            User requester = userService.findById(requesterId);
            boolean administrator = requester != null && "admin".equals(requester.getRole());
            if (!requesterId.equals(asset.getOwnerId()) && !administrator) {
                return error(HttpStatus.FORBIDDEN, "无权访问此图片");
            }
        }

        try {
            byte[] content = mediaAssetService.readContent(asset);
            MediaType contentType;
            try {
                contentType = MediaType.parseMediaType(asset.getContentType());
            } catch (IllegalArgumentException ignored) {
                contentType = MediaType.APPLICATION_OCTET_STREAM;
            }
            ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                    .contentType(contentType)
                    .header(HttpHeaders.X_CONTENT_TYPE_OPTIONS, "nosniff");
            if (Boolean.TRUE.equals(asset.getIsPublic())) {
                response.cacheControl(CacheControl.maxAge(java.time.Duration.ofHours(1)).cachePublic());
            } else {
                response.cacheControl(CacheControl.noStore());
            }
            return response.body(content);
        } catch (IOException e) {
            return error(HttpStatus.NOT_FOUND, "图片文件不存在");
        }
    }

    private String normalizePurpose(String purpose) {
        String normalized = purpose == null ? "" : purpose.trim().toLowerCase(Locale.ROOT);
        if (!MediaAssetService.PURPOSE_ITEM.equals(normalized)
                && !MediaAssetService.PURPOSE_CERTIFICATION.equals(normalized)) {
            throw new IllegalArgumentException("图片用途必须为item或certification");
        }
        return normalized;
    }

    private ImageMetadata inspectImage(byte[] content) throws IOException {
        String contentType = detectContentType(content);
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IllegalArgumentException("无法读取图片内容");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                return new ImageMetadata(contentType, reader.getWidth(0), reader.getHeight(0));
            } finally {
                reader.dispose();
            }
        }
    }

    private String detectContentType(byte[] content) {
        if (content.length >= 3 && (content[0] & 0xFF) == 0xFF
                && (content[1] & 0xFF) == 0xD8 && (content[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (content.length >= 8 && (content[0] & 0xFF) == 0x89 && content[1] == 0x50
                && content[2] == 0x4E && content[3] == 0x47 && content[4] == 0x0D
                && content[5] == 0x0A && content[6] == 0x1A && content[7] == 0x0A) {
            return "image/png";
        }
        if (content.length >= 6 && content[0] == 'G' && content[1] == 'I' && content[2] == 'F'
                && content[3] == '8' && (content[4] == '7' || content[4] == '9') && content[5] == 'a') {
            return "image/gif";
        }
        throw new IllegalArgumentException("仅允许真实的JPEG、PNG或GIF图片");
    }

    private String safeOriginalFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "image";
        }
        String safe = filename.replaceAll("[\\r\\n]", "").trim();
        return safe.length() > 255 ? safe.substring(0, 255) : safe;
    }

    private Long optionalUserId(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        try {
            return jwtUtil.getUserIdFromToken(jwtUtil.extractToken(authorization));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", message);
        return ResponseEntity.status(status).body(response);
    }

    private record ImageMetadata(String contentType, int width, int height) {
    }
}
