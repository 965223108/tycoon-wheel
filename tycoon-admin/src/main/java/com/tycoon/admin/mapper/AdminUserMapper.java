package com.tycoon.admin.mapper;

import com.tycoon.admin.domain.AdminUser;
import org.apache.ibatis.annotations.Param;

public interface AdminUserMapper {

    AdminUser findByUsername(@Param("username") String username);

    int insert(AdminUser user);

    long countAll();
}
