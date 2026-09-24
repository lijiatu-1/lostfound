package com.example.lostfound.service;

import com.example.lostfound.entity.Application;
import java.util.List;

public interface ApplicationService {
    Application findById(Long id);
    List<Application> findByItemId(Long itemId);
    List<Application> findByApplicantId(Long applicantId);
    Application findByItemAndApplicant(Long itemId, Long applicantId, String type);
    Application save(Application application);
    Application submit(Long userId, Long itemId, String content);
    Long handle(Long applicationId, Long publisherId, String action);
    void closePendingByItemId(Long itemId);
    void deleteByItemId(Long itemId);
}
