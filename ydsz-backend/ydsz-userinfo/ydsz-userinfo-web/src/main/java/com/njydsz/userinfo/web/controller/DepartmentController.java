package com.njydsz.userinfo.web.controller;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.njydsz.userinfo.server.service.DepartmentService;
import com.njydsz.userinfo.domain.entity.DepartmentDO;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/dept")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService service;

    @GetMapping("/list")
    public List<DepartmentDO> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public DepartmentDO getById(@PathVariable String id) {
        return service.getById(id);
    }

    @PostMapping
    public String save(@RequestBody DepartmentDO entity) {
        return service.save(entity);
    }

    @PutMapping
    public boolean update(@RequestBody DepartmentDO entity) {
        return service.updateById(entity);
    }

    @DeleteMapping("/{id}")
    public boolean remove(@PathVariable String id) {
        return service.removeById(id);
    }
}
