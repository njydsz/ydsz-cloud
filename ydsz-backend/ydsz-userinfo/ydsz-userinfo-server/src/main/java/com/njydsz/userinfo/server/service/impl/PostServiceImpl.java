package com.njydsz.userinfo.server.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.njydsz.userinfo.server.service.PostService;
import com.njydsz.userinfo.domain.entity.PostDO;
import com.njydsz.userinfo.infra.mapper.PostMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostServiceImpl implements PostService {

    private final PostMapper mapper;

    @Override
    public PostDO getById(String id) {
        return mapper.selectById(id);
    }

    @Override
    public List<PostDO> list() {
        return mapper.selectList(null);
    }

    @Override
    public String save(PostDO entity) {
        mapper.insert(entity);
        return entity.getId();
    }

    @Override
    public boolean updateById(PostDO entity) {
        return mapper.updateById(entity) > 0;
    }

    @Override
    public boolean removeById(String id) {
        return mapper.deleteById(id) > 0;
    }
}
