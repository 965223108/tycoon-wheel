package com.tycoon.user.mapper;

import com.tycoon.user.domain.UserProfile;
import org.apache.ibatis.annotations.Param;

public interface ProfileMapper {

    UserProfile findById(@Param("userId") Long userId);

    int upsert(UserProfile profile);

    int updateStatus(@Param("userId") Long userId,
                     @Param("status") String status,
                     @Param("reason") String reason);
}
