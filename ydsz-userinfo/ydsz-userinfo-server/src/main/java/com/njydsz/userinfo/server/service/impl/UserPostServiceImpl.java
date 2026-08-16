package com.njydsz.userinfo.server.service.impl;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.userinfo.domain.entity.UserPost;
import com.njydsz.userinfo.infra.mapper.UserPostMapper;
import com.njydsz.userinfo.server.service.UserPostService;

/**
 * 用户-岗位关联服务实现。
 *
 * <p>维护用户-岗位的多对多关联 ({@code ydsz_user_post})：支持一人多岗（主岗/兼职），
 *
 * <p>用于工作流审批人解析（按岗位找人）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserPostServiceImpl implements UserPostService {

  private final UserPostMapper mapper;

  @Override
  public UserPost getById(String id) {
    UserPost entity = mapper.selectById(id);
    if (entity == null || entity.getDeleted() == 1) {
      return null;
    }
    return entity;
  }

  @Override
  public List<UserPost> list() {
    LambdaQueryWrapper<UserPost> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserPost::getDeleted, 0);
    return mapper.selectList(wrapper);
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public String save(UserPost entity) {
    mapper.insert(entity);
    return entity.getId();
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public boolean updateById(UserPost entity) {
    return mapper.updateById(entity) > 0;
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public boolean removeById(String id) {
    return mapper.deleteById(id) > 0;
  }
}
