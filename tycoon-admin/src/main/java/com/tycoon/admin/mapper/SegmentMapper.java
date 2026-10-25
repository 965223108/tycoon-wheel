package com.tycoon.admin.mapper;

import com.tycoon.admin.domain.GameSegment;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface SegmentMapper {

    List<GameSegment> listAll();

    List<GameSegment> listEnabled();

    GameSegment findById(@Param("id") Long id);

    GameSegment findByKey(@Param("segKey") String segKey);

    long countAll();

    int insert(GameSegment segment);

    int update(GameSegment segment);

    int delete(@Param("id") Long id);

    int setEnabled(@Param("id") Long id, @Param("enabled") boolean enabled);
}
