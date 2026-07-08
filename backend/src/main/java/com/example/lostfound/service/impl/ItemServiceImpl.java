package com.example.lostfound.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.lostfound.entity.Item;
import com.example.lostfound.mapper.ItemMapper;
import com.example.lostfound.service.ItemService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Service
public class ItemServiceImpl implements ItemService {

    private final ItemMapper itemMapper;

    public ItemServiceImpl(ItemMapper itemMapper) {
        this.itemMapper = itemMapper;
    }

    @Override
    public Item findById(Long id) {
        return itemMapper.selectById(id);
    }

    @Override
    public Page<Item> findActiveItems(String type, String category, int page, int pageSize) {
        Page<Item> p = new Page<>(page, pageSize);
        QueryWrapper<Item> wrapper = new QueryWrapper<>();
        wrapper.eq("status", "active")
               .gt("expire_at", LocalDateTime.now())
               .orderByDesc("created_at");
        if (type != null && !type.isEmpty()) {
            wrapper.eq("type", type);
        }
        if (category != null && !category.isEmpty()) {
            wrapper.eq("category", category);
        }
        return itemMapper.selectPage(p, wrapper);
    }

    @Override
    public Page<Item> findByPublisherId(Long publisherId, int page, int pageSize) {
        Page<Item> p = new Page<>(page, pageSize);
        QueryWrapper<Item> wrapper = new QueryWrapper<>();
        wrapper.eq("publisher_id", publisherId)
               .orderByDesc("created_at");
        return itemMapper.selectPage(p, wrapper);
    }

    @Override
    @Transactional
    public Item save(Item item) {
        LocalDateTime now = LocalDateTime.now();
        if (item.getId() == null) {
            if (item.getCreatedAt() == null) {
                item.setCreatedAt(now);
            }
            if (item.getExpireAt() == null) {
                item.setExpireAt(now.plusDays(7));
            }
            if (item.getStatus() == null) {
                item.setStatus("active");
            }
            itemMapper.insert(item);
        } else {
            item.setUpdatedAt(now);
            itemMapper.updateById(item);
        }
        return item;
    }

    @Override
    @Transactional
    public Item update(Item item) {
        item.setUpdatedAt(LocalDateTime.now());
        itemMapper.updateById(item);
        return item;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        itemMapper.deleteById(id);
    }

    @Override
    public Page<Item> search(String keyword, int page, int pageSize) {
        if (keyword == null || keyword.isEmpty()) {
            return findActiveItems(null, null, page, pageSize);
        }
        Page<Item> p = new Page<>(page, pageSize);
        QueryWrapper<Item> wrapper = new QueryWrapper<>();
        wrapper.eq("status", "active")
               .gt("expire_at", LocalDateTime.now())
               .and(w -> w.like("title", keyword)
                           .or().like("description", keyword)
                           .or().like("location_name", keyword)
                           .or().like("tags", keyword))
               .orderByDesc("created_at");
        return itemMapper.selectPage(p, wrapper);
    }

    @Override
    @Transactional
    public void updateStatus(Long id, String status) {
        Item item = itemMapper.selectById(id);
        if (item != null) {
            item.setStatus(status);
            item.setUpdatedAt(LocalDateTime.now());
            itemMapper.updateById(item);
        }
    }

    @Override
    public List<String> getCategories() {
        return Arrays.asList("证件卡片", "电子产品", "服饰配件", "学习用品", "生活用品", "其他物品");
    }
}
