package com.tycoon.admin.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 把 /admin 重定向到静态页面，方便直接访问。
 */
@RestController
public class AdminPageController {

    @GetMapping({"/admin", "/admin/"})
    public Map<String, String> index() {
        return Map.of("hint", "管理后台页面位于 /admin/index.html");
    }
}
