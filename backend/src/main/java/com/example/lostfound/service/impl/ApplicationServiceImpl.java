package com.example.lostfound.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.lostfound.entity.Application;
import com.example.lostfound.mapper.ApplicationMapper;
import com.example.lostfound.service.ApplicationService;
import com.example.lostfound.service.ItemService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ApplicationServiceImpl implements ApplicationService {

    private final ApplicationMapper applicationMapper;
    private final ItemService itemService;

    public ApplicationServiceImpl(ApplicationMapper applicationMapper, ItemService itemService) {
        this.applicationMapper = applicationMapper;
        this.itemService = itemService;
    }

    @Override
    public Application findById(Long id) {
        return applicationMapper.selectById(id);
    }

    @Override
    public List<Application> findByItemId(Long itemId) {
        return applicationMapper.findByItemId(itemId);
    }

    @Override
    public List<Application> findByApplicantId(Long applicantId) {
        return applicationMapper.findByApplicantId(applicantId);
    }

    @Override
    @Transactional
    public Application save(Application application) {
        if (application.getId() == null) {
            application.setCreatedAt(LocalDateTime.now());
            application.setStatus("pending");
            applicationMapper.insert(application);
        } else {
            applicationMapper.updateById(application);
        }
        return application;
    }

    @Override
    @Transactional
    public void handleApplication(Long id, String action) {
        Application application = applicationMapper.selectById(id);
        if (application != null) {
            application.setStatus("accept".equalsIgnoreCase(action) ? "accepted" : "rejected");
            applicationMapper.updateById(application);
        }
    }

    @Override
    public Application findByItemAndApplicant(Long itemId, Long applicantId, String type) {
        return applicationMapper.findByItemAndApplicant(itemId, applicantId, type);
    }

    @Override
    @Transactional
    public void rejectOtherApplications(Long itemId, Long excludeId) {
        QueryWrapper<Application> wrapper = new QueryWrapper<>();
        wrapper.eq("item_id", itemId)
               .eq("status", "pending")
               .ne("id", excludeId);
        Application update = new Application();
        update.setStatus("rejected");
        applicationMapper.update(update, wrapper);
    }

    @Override
    @Transactional
    public void deleteByItemId(Long itemId) {
        QueryWrapper<Application> wrapper = new QueryWrapper<>();
        wrapper.eq("item_id", itemId);
        applicationMapper.delete(wrapper);
    }

    @Override
    @Transactional
    public void acceptAndResolve(Long applicationId, Long itemId) {
        // 1. 接受该申请
        Application application = applicationMapper.selectById(applicationId);
        if (application != null) {
            application.setStatus("accepted");
            applicationMapper.updateById(application);
        }
        // 2. 拒绝同物品其他待处理申请
        rejectOtherApplications(itemId, applicationId);
        // 3. 更新物品状态为 resolved
        itemService.updateStatus(itemId, "resolved");
    }
}