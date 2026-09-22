package com.example.lostfound.service.impl;

import com.example.lostfound.entity.MediaAsset;
import com.example.lostfound.mapper.MediaAssetMapper;
import com.example.lostfound.service.MediaAssetService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MediaAssetServiceImpl implements MediaAssetService {

    private static final int MAX_ITEM_ASSETS = 9;

    private final MediaAssetMapper mediaAssetMapper;
    private final ObjectMapper objectMapper;
    private final Path uploadRoot;

    public MediaAssetServiceImpl(MediaAssetMapper mediaAssetMapper,
                                 ObjectMapper objectMapper,
                                 @Value("${media.upload-dir:uploads/media}") String uploadDir) {
        this.mediaAssetMapper = mediaAssetMapper;
        this.objectMapper = objectMapper;
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    @Override
    @Transactional
    public MediaAsset save(MediaAsset asset) {
        if (asset.getId() == null) {
            asset.setCreatedAt(LocalDateTime.now());
            mediaAssetMapper.insert(asset);
        } else {
            mediaAssetMapper.updateById(asset);
        }
        return asset;
    }

    @Override
    public MediaAsset findById(Long assetId) {
        return assetId == null ? null : mediaAssetMapper.selectById(assetId);
    }

    @Override
    public MediaAsset requireOwnedAsset(Long ownerId, Long assetId, String purpose) {
        if (ownerId == null || assetId == null || assetId <= 0) {
            throw new IllegalArgumentException("图片资源参数无效");
        }
        MediaAsset asset = findById(assetId);
        if (asset == null || !ownerId.equals(asset.getOwnerId())) {
            throw new IllegalArgumentException("图片资源不存在或不属于当前用户");
        }
        if (!purpose.equals(asset.getPurpose())) {
            throw new IllegalArgumentException("图片资源用途不匹配");
        }
        return asset;
    }

    @Override
    public String normalizeItemAssetIds(Long ownerId, Collection<Long> assetIds) {
        if (assetIds == null || assetIds.isEmpty()) {
            return "[]";
        }
        if (assetIds.size() > MAX_ITEM_ASSETS) {
            throw new IllegalArgumentException("最多上传9张物品图片");
        }

        LinkedHashSet<Long> uniqueIds = new LinkedHashSet<>();
        for (Long assetId : assetIds) {
            if (assetId == null || assetId <= 0 || !uniqueIds.add(assetId)) {
                throw new IllegalArgumentException("图片资源ID无效或重复");
            }
        }
        for (Long assetId : uniqueIds) {
            requireOwnedAsset(ownerId, assetId, PURPOSE_ITEM);
        }
        try {
            return objectMapper.writeValueAsString(new ArrayList<>(uniqueIds));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("无法保存图片资源", e);
        }
    }

    @Override
    public List<MediaAsset> findItemAssets(Collection<Long> assetIds) {
        if (assetIds == null || assetIds.isEmpty()) {
            return List.of();
        }
        List<Long> orderedIds = assetIds.stream().filter(Objects::nonNull).toList();
        Map<Long, MediaAsset> byId = mediaAssetMapper.selectBatchIds(orderedIds).stream()
                .filter(asset -> PURPOSE_ITEM.equals(asset.getPurpose()) && Boolean.TRUE.equals(asset.getIsPublic()))
                .collect(Collectors.toMap(MediaAsset::getId, Function.identity()));
        return orderedIds.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    @Override
    public byte[] readContent(MediaAsset asset) throws IOException {
        if (asset == null || asset.getStorageKey() == null) {
            throw new IllegalArgumentException("图片资源不存在");
        }
        Path path = uploadRoot.resolve(asset.getStorageKey()).normalize();
        if (!path.startsWith(uploadRoot)) {
            throw new IllegalArgumentException("图片资源路径无效");
        }
        return Files.readAllBytes(path);
    }

    @Override
    public String contentUrl(Long assetId) {
        return "/api/assets/" + assetId + "/content";
    }
}
