package com.tycoon.mapper;

import com.tycoon.domain.SessionToken;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;

@Mapper
public interface SessionMapper {

    int insert(SessionToken session);

    /** 只返回未过期的会话 */
    SessionToken findValid(@Param("token") String token, @Param("now") Instant now);

    int deleteByToken(@Param("token") String token);

    int deleteExpired(@Param("now") Instant now);

    int countByToken(@Param("token") String token);
}
