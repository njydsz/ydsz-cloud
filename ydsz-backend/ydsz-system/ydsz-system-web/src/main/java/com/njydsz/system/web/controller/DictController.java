package com.njydsz.system.web.controller;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.njydsz.system.server.service.DictService;
import com.njydsz.system.domain.entity.DictTypeDO;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/dict")
@RequiredArgsConstructor
public class DictController {

    private final DictService service;

    @GetMapping("/list")
    public List<DictTypeDO> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public DictTypeDO getById(@PathVariable String id) {
        return service.getById(id);
    }

    @PostMapping
    public String save(@RequestBody DictTypeDO entity) {
        return service.save(entity);
    }

    @PutMapping
    public boolean update(@RequestBody DictTypeDO entity) {
        return service.updateById(entity);
    }

    @DeleteMapping("/{id}")
    public boolean remove(@PathVariable String id) {
        return service.removeById(id);
    }
}
