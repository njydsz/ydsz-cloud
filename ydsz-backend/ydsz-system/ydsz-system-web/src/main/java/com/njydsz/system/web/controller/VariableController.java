package com.njydsz.system.web.controller;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.njydsz.system.server.service.VariableService;
import com.njydsz.system.domain.entity.VariableDO;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/variable")
@RequiredArgsConstructor
public class VariableController {

    private final VariableService service;

    @GetMapping("/list")
    public List<VariableDO> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public VariableDO getById(@PathVariable String id) {
        return service.getById(id);
    }

    @PostMapping
    public String save(@RequestBody VariableDO entity) {
        return service.save(entity);
    }

    @PutMapping
    public boolean update(@RequestBody VariableDO entity) {
        return service.updateById(entity);
    }

    @DeleteMapping("/{id}")
    public boolean remove(@PathVariable String id) {
        return service.removeById(id);
    }
}
