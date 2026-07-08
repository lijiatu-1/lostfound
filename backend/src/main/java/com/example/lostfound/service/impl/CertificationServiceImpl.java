package com.example.lostfound.service.impl;

import com.example.lostfound.entity.Certification;
import com.example.lostfound.mapper.CertificationMapper;
import com.example.lostfound.service.CertificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CertificationServiceImpl implements CertificationService {

    private final CertificationMapper certificationMapper;

    public CertificationServiceImpl(CertificationMapper certificationMapper) {
        this.certificationMapper = certificationMapper;
    }

    @Override
    public Certification findById(Long id) {
        return certificationMapper.selectById(id);
    }

    @Override
    @Transactional
    public Certification update(Certification certification) {
        certificationMapper.updateById(certification);
        return certification;
    }

    @Override
    @Transactional
    public Certification save(Certification certification) {
        if (certification.getId() == null) {
            certification.setCreatedAt(LocalDateTime.now());
            certification.setStatus("pending");
            certificationMapper.insert(certification);
        } else {
            certificationMapper.updateById(certification);
        }
        return certification;
    }

    @Override
    @Transactional
    public void updateStatus(Long id, String status, Long reviewerId, String reviewMsg) {
        Certification cert = certificationMapper.selectById(id);
        if (cert != null) {
            cert.setStatus(status);
            cert.setReviewerId(reviewerId);
            cert.setReviewMsg(reviewMsg);
            certificationMapper.updateById(cert);
        }
    }

    @Override
    public List<Certification> findByUserId(Long userId) {
        return certificationMapper.findByUserId(userId);
    }

    @Override
    public List<Certification> findPending() {
        return certificationMapper.findByStatus("pending");
    }
}
