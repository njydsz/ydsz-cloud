package com.njydsz.userinfo.web.controller;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.njydsz.userinfo.server.service.PostService;
import com.njydsz.userinfo.domain.entity.PostDO;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/post")
@RequiredArgsConstructor
public class PostController {

    private final PostService service;

    @GetMapping("/list")
    public List<PostDO> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public PostDO getById(@PathVariable String id) {
        return service.getById(id);
    }

    @PostMapping
    public String save(@RequestBody PostDO entity) {
        return service.save(entity);
    }

    @PutMapping
    public boolean update(@RequestBody PostDO entity) {
        return service.updateById(entity);
    }

    @DeleteMapping("/{id}")
    public boolean remove(@PathVariable String id) {
        return service.removeById(id);
    }
}
