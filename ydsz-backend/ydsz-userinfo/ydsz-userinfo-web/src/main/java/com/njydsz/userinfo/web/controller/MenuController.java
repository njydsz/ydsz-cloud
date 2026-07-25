package com.njydsz.userinfo.web.controller;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.njydsz.userinfo.server.service.MenuService;
import com.njydsz.userinfo.domain.entity.MenuDO;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/menu")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService service;

    @GetMapping("/list")
    public List<MenuDO> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public MenuDO getById(@PathVariable String id) {
        return service.getById(id);
    }

    @PostMapping
    public String save(@RequestBody MenuDO entity) {
        return service.save(entity);
    }

    @PutMapping
    public boolean update(@RequestBody MenuDO entity) {
        return service.updateById(entity);
    }

    @DeleteMapping("/{id}")
    public boolean remove(@PathVariable String id) {
        return service.removeById(id);
    }
}
