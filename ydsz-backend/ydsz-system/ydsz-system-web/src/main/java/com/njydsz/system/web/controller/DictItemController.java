package com.njydsz.system.web.controller;

import java.util.List;
import org.springframework.web.bind.annotation.*;
import com.njydsz.system.server.service.DictItemService;
import com.njydsz.system.domain.entity.DictItemDO;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/dict/item")
@RequiredArgsConstructor
public class DictItemController {

    private final DictItemService service;

    @GetMapping("/list")
    public List<DictItemDO> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public DictItemDO getById(@PathVariable String id) {
        return service.getById(id);
    }

    @PostMapping
    public String save(@RequestBody DictItemDO entity) {
        return service.save(entity);
    }

    @PutMapping
    public boolean update(@RequestBody DictItemDO entity) {
        return service.updateById(entity);
    }

    @DeleteMapping("/{id}")
    public boolean remove(@PathVariable String id) {
        return service.removeById(id);
    }
}
