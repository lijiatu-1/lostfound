package com.example.lostfound.service.impl;

import com.example.lostfound.entity.AiUsage;
import com.example.lostfound.mapper.AiUsageMapper;
import com.example.lostfound.service.AiUsageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

@Service
public class AiUsageServiceImpl implements AiUsageService {

    private final AiUsageMapper aiUsageMapper;
    private final int dailyLimit;

    public AiUsageServiceImpl(AiUsageMapper aiUsageMapper,
                              @Value("${ai.daily-limit:5}") int dailyLimit) {
        this.aiUsageMapper = aiUsageMapper;
        // The consented transfer scope is at most five images per person and day.
        this.dailyLimit = Math.max(1, Math.min(5, dailyLimit));
    }

    @Override
    @Transactional
    public boolean tryConsume(Long userId) {
        if (userId == null) {
            return false;
        }
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        if (aiUsageMapper.incrementIfBelowLimit(userId, today, dailyLimit) == 1) {
            return true;
        }

        AiUsage firstUse = new AiUsage();
        firstUse.setUserId(userId);
        firstUse.setUsageDate(today);
        firstUse.setRequestCount(1);
        try {
            aiUsageMapper.insert(firstUse);
            return true;
        } catch (DuplicateKeyException duplicate) {
            // Another request created today's row between update and insert.
            return aiUsageMapper.incrementIfBelowLimit(userId, today, dailyLimit) == 1;
        }
    }
}
