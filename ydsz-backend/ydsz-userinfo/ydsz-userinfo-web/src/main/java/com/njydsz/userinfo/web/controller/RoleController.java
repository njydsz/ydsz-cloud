package com.njydsz.userinfo.web.controller;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.njydsz.userinfo.server.service.RoleService;
import com.njydsz.userinfo.domain.entity.RoleDO;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/role")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService service;

    @GetMapping("/list")
    public List<RoleDO> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public RoleDO getById(@PathVariable String id) {
        return service.getById(id);
    }

    @PostMapping
    public String save(@RequestBody RoleDO entity) {
        return service.save(entity);
    }

    @PutMapping
    public boolean update(@RequestBody RoleDO entity) {
        return service.updateById(entity);
    }

    @DeleteMapping("/{id}")
    public boolean remove(@PathVariable String id) {
        return service.removeById(id);
    }
}
