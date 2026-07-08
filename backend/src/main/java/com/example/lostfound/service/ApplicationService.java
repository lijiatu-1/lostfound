package com.example.lostfound.service;

import com.example.lostfound.entity.Application;

import java.util.List;

public interface ApplicationService {

    Application findById(Long id);

    List<Application> findByItemId(Long itemId);

    List<Application> findByApplicantId(Long applicantId);

    Application findByItemAndApplicant(Long itemId, Long applicantId, String type);

    Application save(Application application);

    void handleApplication(Long id, String action);

    void rejectOtherApplications(Long itemId, Long excludeId);

    /**
     * 原子性操作：接受申请 + 拒绝同物品其他申请 + 更新物品状态为 resolved
     * 整个操作在一个事务内完成，防止中间状态不一致
     */
    void acceptAndResolve(Long applicationId, Long itemId);

    void deleteByItemId(Long itemId);
}