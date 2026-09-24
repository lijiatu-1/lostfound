package com.example.lostfound.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.lostfound.entity.Item;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ItemMapper extends BaseMapper<Item> {
    @Select("SELECT id, publisher_id, type, category, title, description, location_name, "
            + "images, tags, status, created_at, expire_at, updated_at "
            + "FROM items WHERE id = #{id} FOR UPDATE")
    Item lockById(@Param("id") Long id);
}
