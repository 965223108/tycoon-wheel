package com.tycoon.user.mapper;

import com.tycoon.user.domain.UserReward;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface RewardMapper {

    int insert(UserReward reward);

    UserReward findById(@Param("id") Long id);

    List<UserReward> listByUser(@Param("userId") Long userId,
                                @Param("offset") int offset,
                                @Param("size") int size);

    long countByUser(@Param("userId") Long userId);

    int updateStatus(@Param("id") Long id,
                     @Param("status") String status,
                     @Param("remark") String remark);
}
