package com.example.lostfound.service;

import com.example.lostfound.entity.MediaAsset;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

public interface MediaAssetService {

    String PURPOSE_ITEM = "item";
    String PURPOSE_CERTIFICATION = "certification";

    MediaAsset save(MediaAsset asset);

    MediaAsset findById(Long assetId);

    MediaAsset requireOwnedAsset(Long ownerId, Long assetId, String purpose);

    /**
     * Validates a publisher-owned list of item assets and returns its canonical
     * JSON representation for storage in Item.images.
     */
    String normalizeItemAssetIds(Long ownerId, Collection<Long> assetIds);

    List<MediaAsset> findItemAssets(Collection<Long> assetIds);

    byte[] readContent(MediaAsset asset) throws IOException;

    String contentUrl(Long assetId);
}
