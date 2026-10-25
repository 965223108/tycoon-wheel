package com.tycoon.mapper;

import com.tycoon.domain.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {

    User findByUsername(@Param("username") String username);

    User findById(@Param("id") Long id);

    int insert(User user);

    /**
     * 乐观锁更新：WHERE 带旧 version，返回受影响行数。
     * 返回 0 说明这条记录已被别的请求改过，调用方应提示重试。
     */
    int updateWithVersion(User user);

    long countAll();
}
