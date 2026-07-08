package com.example.lostfound.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.lostfound.entity.Item;

import java.util.List;

public interface ItemService {

    Item findById(Long id);

    Page<Item> findActiveItems(String type, String category, int page, int pageSize);

    Page<Item> findByPublisherId(Long publisherId, int page, int pageSize);

    Item save(Item item);

    Item update(Item item);

    void delete(Long id);

    Page<Item> search(String keyword, int page, int pageSize);

    void updateStatus(Long id, String status);

    List<String> getCategories();
}