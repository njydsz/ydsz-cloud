package com.njydsz.userinfo.server.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.userinfo.domain.entity.UserFieldDO;
import com.njydsz.userinfo.infra.mapper.UserFieldMapper;
import com.njydsz.userinfo.server.service.UserFieldService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 用户自定义字段 Service 实现。
 *
 * <p>内部关联表服务，供其他 Service 内部调用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserFieldServiceImpl implements UserFieldService {

    private final UserFieldMapper mapper;

    @Override
    public UserFieldDO getById(String id) {
        UserFieldDO entity = mapper.selectById(id);
        if (entity == null || entity.getDeleted() == 1) {
            return null;
        }
        return entity;
    }

    @Override
    public List<UserFieldDO> list() {
        LambdaQueryWrapper<UserFieldDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFieldDO::getDeleted, 0);
        return mapper.selectList(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String save(UserFieldDO entity) {
        mapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(UserFieldDO entity) {
        return mapper.updateById(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) {
        return mapper.deleteById(id) > 0;
    }
}
