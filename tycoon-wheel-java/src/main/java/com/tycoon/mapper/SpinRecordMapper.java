package com.tycoon.mapper;

import com.tycoon.domain.SpinRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SpinRecordMapper {

    int insert(SpinRecord record);

    List<SpinRecord> findRecent(@Param("userId") Long userId, @Param("limit") int limit);

    long countAll();

    long countByType(@Param("type") String type);

    /** 轮盘发出的积分合计（用于运营统计） */
    long sumPointsAwarded();
}
