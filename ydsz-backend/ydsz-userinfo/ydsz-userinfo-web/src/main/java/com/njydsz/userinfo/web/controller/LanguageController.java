package com.njydsz.userinfo.web.controller;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.njydsz.userinfo.server.service.LanguageService;
import com.njydsz.userinfo.domain.entity.LanguageDO;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/language")
@RequiredArgsConstructor
public class LanguageController {

    private final LanguageService service;

    @GetMapping("/list")
    public List<LanguageDO> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public LanguageDO getById(@PathVariable String id) {
        return service.getById(id);
    }

    @PostMapping
    public String save(@RequestBody LanguageDO entity) {
        return service.save(entity);
    }

    @PutMapping
    public boolean update(@RequestBody LanguageDO entity) {
        return service.updateById(entity);
    }

    @DeleteMapping("/{id}")
    public boolean remove(@PathVariable String id) {
        return service.removeById(id);
    }
}
