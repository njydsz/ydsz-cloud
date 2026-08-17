package com.njydsz.userinfo.server.service.impl;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.userinfo.domain.entity.UserDept;
import com.njydsz.userinfo.infra.repository.UserDeptRepository;
import com.njydsz.userinfo.server.service.UserDeptService;

/**
 * 用户-部门关联服务实现。
 *
 * <p>维护用户-部门的多对多关联 ({@code ydsz_user_dept})：
 *
 * <p>支持主部门（{@code isPrimary}）标识，辅助部门（兼任）多选。
 *
 * <p>用户权限合并时按主部门优先 + 辅助部门叠加。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserDeptServiceImpl implements UserDeptService {

  private final UserDeptRepository userDeptRepository;

  @Override
  public UserDept getById(String id) {
    UserDept entity = userDeptRepository.findById(id);
    if (entity == null || entity.getDeleted() == 1) {
      return null;
    }
    return entity;
  }

  @Override
  public List<UserDept> list() {
    LambdaQueryWrapper<UserDept> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserDept::getDeleted, 0);
    return userDeptRepository.list(wrapper);
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public String save(UserDept entity) {
    userDeptRepository.insert(entity);
    return entity.getId();
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public boolean updateById(UserDept entity) {
    return userDeptRepository.updateById(entity) > 0;
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public boolean removeById(String id) {
    return userDeptRepository.deleteById(id) > 0;
  }
}
