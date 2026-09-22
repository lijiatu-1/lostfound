package com.example.lostfound.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.lostfound.entity.AiUsage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;

@Mapper
public interface AiUsageMapper extends BaseMapper<AiUsage> {

    /**
     * Atomically consumes one request only while the current count is below the
     * supplied cap.  The caller inserts the first row when this returns zero.
     */
    @Update("UPDATE ai_usage SET request_count = request_count + 1 "
            + "WHERE user_id = #{userId} AND usage_date = #{usageDate} "
            + "AND request_count < #{limit}")
    int incrementIfBelowLimit(@Param("userId") Long userId,
                              @Param("usageDate") LocalDate usageDate,
                              @Param("limit") int limit);
}
