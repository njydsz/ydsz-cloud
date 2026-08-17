package com.njydsz.userinfo.server.service.impl;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.userinfo.infra.entity.UserPostDO;
import com.njydsz.userinfo.infra.repository.UserPostRepository;
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

  private final UserPostRepository userPostRepository;

  @Override
  public UserPostDO getById(String id) {
    UserPostDO entity = userPostRepository.findById(id);
    if (entity == null || entity.getDeleted() == 1) {
      return null;
    }
    return entity;
  }

  @Override
  public List<UserPostDO> list() {
    LambdaQueryWrapper<UserPostDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserPostDO::getDeleted, 0);
    return userPostRepository.list(wrapper);
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public String save(UserPostDO entity) {
    userPostRepository.insert(entity);
    return entity.getId();
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public boolean updateById(UserPostDO entity) {
    return userPostRepository.updateById(entity) > 0;
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public boolean removeById(String id) {
    return userPostRepository.deleteById(id) > 0;
  }
}
