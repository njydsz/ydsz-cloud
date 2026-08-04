package com.remisoft.userinfo.server.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.remisoft.userinfo.domain.entity.UserField;
import com.remisoft.userinfo.infra.mapper.UserFieldMapper;
import com.remisoft.userinfo.server.service.UserFieldService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 用户扩展字段服务实现。
 *
 * <p>维护用户信息扩展字段配置 ({@code remi_user_field})：支持文本、数字、日期、下拉、文件等多种类型，
 *
 * <p>用于租户自定义员工档案（工号、职级、入职日期、紧急联系人等）。
 *
 * @author remi-team
 * @since 1.0.0
 */

@Slf4j
@Service
@RequiredArgsConstructor
public class UserFieldServiceImpl implements UserFieldService {

    private final UserFieldMapper mapper;

    @Override
    public UserField getById(String id) {
        UserField entity = mapper.selectById(id);
        if (entity == null || entity.getDeleted() == 1) {
            return null;
        }
        return entity;
    }

    @Override
    public List<UserField> list() {
        LambdaQueryWrapper<UserField> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserField::getDeleted, 0);
        return mapper.selectList(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String save(UserField entity) {
        mapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(UserField entity) {
        return mapper.updateById(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) {
        return mapper.deleteById(id) > 0;
    }
}
