package com.example.lostfound.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.lostfound.entity.Certification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CertificationMapper extends BaseMapper<Certification> {

    @Select("SELECT * FROM certifications WHERE user_id = #{userId} ORDER BY created_at DESC")
    List<Certification> findByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM certifications WHERE status = #{status} ORDER BY created_at DESC")
    List<Certification> findByStatus(@Param("status") String status);
}
