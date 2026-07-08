package com.example.lostfound.service;

import com.example.lostfound.entity.Certification;

import java.util.List;

public interface CertificationService {

    Certification findById(Long id);

    Certification save(Certification certification);

    Certification update(Certification certification);

    void updateStatus(Long id, String status, Long reviewerId, String reviewMsg);

    List<Certification> findByUserId(Long userId);

    List<Certification> findPending();
}
