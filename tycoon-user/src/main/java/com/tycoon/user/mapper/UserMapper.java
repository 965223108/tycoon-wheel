package com.tycoon.user.mapper;

import com.tycoon.user.domain.User;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface UserMapper {

    User findById(@Param("id") Long id);

    User findByUsername(@Param("username") String username);

    long countByUsername(@Param("username") String username);

    int insert(User user);

    /**
     * 原子扣分 + 累计抽奖次数 + 当日计数。积分不足时影响行数为 0。
     */
    int deductPoints(@Param("id") Long id,
                     @Param("cost") int cost,
                     @Param("dayKey") String dayKey);

    int addPoints(@Param("id") Long id, @Param("delta") int delta);

    List<User> list(@Param("keyword") String keyword,
                    @Param("offset") int offset,
                    @Param("size") int size);

    long count(@Param("keyword") String keyword);
}
