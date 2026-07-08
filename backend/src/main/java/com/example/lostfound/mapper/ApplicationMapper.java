package com.example.lostfound.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.lostfound.entity.Application;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ApplicationMapper extends BaseMapper<Application> {

    @Select("SELECT * FROM applications WHERE item_id = #{itemId} ORDER BY created_at DESC")
    List<Application> findByItemId(@Param("itemId") Long itemId);

    @Select("SELECT * FROM applications WHERE applicant_id = #{applicantId} ORDER BY created_at DESC")
    List<Application> findByApplicantId(@Param("applicantId") Long applicantId);

    @Select("SELECT * FROM applications WHERE item_id = #{itemId} AND applicant_id = #{applicantId} AND type = #{type}")
    Application findByItemAndApplicant(@Param("itemId") Long itemId, @Param("applicantId") Long applicantId, @Param("type") String type);
}