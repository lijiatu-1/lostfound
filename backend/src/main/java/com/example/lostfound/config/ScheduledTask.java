package com.example.lostfound.config;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.example.lostfound.entity.Item;
import com.example.lostfound.mapper.ItemMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class ScheduledTask {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTask.class);

    private final ItemMapper itemMapper;

    public ScheduledTask(ItemMapper itemMapper) {
        this.itemMapper = itemMapper;
    }

    // fixedDelay：上次执行完毕后间隔 60 秒再执行，避免重叠
    @Scheduled(fixedDelay = 60000, initialDelay = 10000)
    public void expireItems() {
        try {
            UpdateWrapper<Item> wrapper = new UpdateWrapper<>();
            wrapper.set("status", "expired")
                   .eq("status", "active")
                   .lt("expire_at", LocalDateTime.now());
            int count = itemMapper.update(null, wrapper);
            if (count > 0) {
                log.info("定时任务：已将 {} 个过期物品标记为 expired", count);
            }
        } catch (Exception e) {
            log.error("定时过期任务执行失败", e);
        }
    }
}
