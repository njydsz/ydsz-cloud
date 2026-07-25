package com.njydsz.userinfo.server.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.njydsz.userinfo.domain.entity.PostDO;
import com.njydsz.userinfo.domain.enums.UserInfoResultCode;
import com.njydsz.userinfo.domain.exception.BusinessException;
import com.njydsz.userinfo.infra.mapper.PostMapper;
import com.njydsz.userinfo.server.service.PostService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;

/**
 * 岗位 Service 实现。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class PostServiceImpl implements PostService {

    private final PostMapper mapper;

    @Override
    public PostDO getById(String id) {
        PostDO entity = mapper.selectById(id);
        if (entity == null || entity.getDeleted() == 1) {
            throw new BusinessException(UserInfoResultCode.POST_NOT_FOUND);
        }
        return entity;
    }

    @Override
    public List<PostDO> list() {
        LambdaQueryWrapper<PostDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PostDO::getDeleted, 0);
        wrapper.orderByAsc(PostDO::getSortOrder);
        return mapper.selectList(wrapper);
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
