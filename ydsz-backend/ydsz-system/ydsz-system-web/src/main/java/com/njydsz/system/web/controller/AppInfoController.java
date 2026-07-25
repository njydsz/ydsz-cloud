package com.njydsz.system.web.controller;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.njydsz.system.server.service.AppInfoService;
import com.njydsz.system.domain.entity.AppInfoDO;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/app")
@RequiredArgsConstructor
public class AppInfoController {

    private final AppInfoService service;

    @GetMapping("/list")
    public List<AppInfoDO> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public AppInfoDO getById(@PathVariable String id) {
        return service.getById(id);
    }

    @PostMapping
    public String save(@RequestBody AppInfoDO entity) {
        return service.save(entity);
    }

    @PutMapping
    public boolean update(@RequestBody AppInfoDO entity) {
        return service.updateById(entity);
    }

    @DeleteMapping("/{id}")
    public boolean remove(@PathVariable String id) {
        return service.removeById(id);
    }
}
