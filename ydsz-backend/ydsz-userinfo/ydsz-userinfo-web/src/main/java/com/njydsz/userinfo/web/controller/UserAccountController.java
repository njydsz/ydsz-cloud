package com.njydsz.userinfo.web.controller;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.njydsz.userinfo.server.service.UserAccountService;
import com.njydsz.userinfo.domain.entity.UserAccountDO;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserAccountController {

    private final UserAccountService service;

    @GetMapping("/list")
    public List<UserAccountDO> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public UserAccountDO getById(@PathVariable String id) {
        return service.getById(id);
    }

    @PostMapping
    public String save(@RequestBody UserAccountDO entity) {
        return service.save(entity);
    }

    @PutMapping
    public boolean update(@RequestBody UserAccountDO entity) {
        return service.updateById(entity);
    }

    @DeleteMapping("/{id}")
    public boolean remove(@PathVariable String id) {
        return service.removeById(id);
    }
}
