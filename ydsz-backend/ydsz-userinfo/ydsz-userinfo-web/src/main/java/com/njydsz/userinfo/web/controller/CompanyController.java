package com.njydsz.userinfo.web.controller;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.njydsz.userinfo.server.service.CompanyService;
import com.njydsz.userinfo.domain.entity.CompanyDO;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/company")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyService service;

    @GetMapping("/list")
    public List<CompanyDO> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public CompanyDO getById(@PathVariable String id) {
        return service.getById(id);
    }

    @PostMapping
    public String save(@RequestBody CompanyDO entity) {
        return service.save(entity);
    }

    @PutMapping
    public boolean update(@RequestBody CompanyDO entity) {
        return service.updateById(entity);
    }

    @DeleteMapping("/{id}")
    public boolean remove(@PathVariable String id) {
        return service.removeById(id);
    }
}
