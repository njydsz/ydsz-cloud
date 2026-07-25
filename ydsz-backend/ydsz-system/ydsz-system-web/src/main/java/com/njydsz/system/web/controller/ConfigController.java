package com.njydsz.system.web.controller;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.njydsz.system.server.service.ConfigService;
import com.njydsz.system.domain.entity.ConfigDO;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/config")
@RequiredArgsConstructor
public class ConfigController {

    private final ConfigService service;

    @GetMapping("/list")
    public List<ConfigDO> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public ConfigDO getById(@PathVariable String id) {
        return service.getById(id);
    }

    @PostMapping
    public String save(@RequestBody ConfigDO entity) {
        return service.save(entity);
    }

    @PutMapping
    public boolean update(@RequestBody ConfigDO entity) {
        return service.updateById(entity);
    }

    @DeleteMapping("/{id}")
    public boolean remove(@PathVariable String id) {
        return service.removeById(id);
    }
}
