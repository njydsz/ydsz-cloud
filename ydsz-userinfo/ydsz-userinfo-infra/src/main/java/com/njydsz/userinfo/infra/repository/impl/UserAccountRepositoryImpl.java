package com.njydsz.userinfo.infra.repository.impl;

import java.util.Collection;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.userinfo.infra.repository.UserAccountRepository;
import com.njydsz.userinfo.infra.entity.UserAccountDO;
import com.njydsz.userinfo.infra.mapper.UserAccountMapper;

/**
 * 用户账号 Repository 实现
 *
 * <p>基于 MyBatis-Plus 的 {@link UserAccountMapper} 实现用户账号的数据访问。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class UserAccountRepositoryImpl implements UserAccountRepository {

  private final UserAccountMapper userAccountMapper;

  @Override
  public UserAccountDO findById(String id) {
    return userAccountMapper.selectById(id);
  }

  @Override
  public UserAccountDO findByUsername(String username) {
    LambdaQueryWrapper<UserAccountDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserAccountDO::getUsername, username);
    return userAccountMapper.selectOne(wrapper);
  }

  @Override
  public UserAccountDO save(UserAccountDO entity) {
    if (entity.getId() == null || entity.getId().isBlank()) {
      userAccountMapper.insert(entity);
    } else {
      userAccountMapper.updateById(entity);
    }
    return entity;
  }

  @Override
  public int insert(UserAccountDO entity) {
    return userAccountMapper.insert(entity);
  }

  @Override
  public int updateById(UserAccountDO entity) {
    return userAccountMapper.updateById(entity);
  }

  @Override
  public int deleteById(String id) {
    return userAccountMapper.deleteById(id);
  }

  @Override
  public Page<UserAccountDO> page(Page<UserAccountDO> page, LambdaQueryWrapper<UserAccountDO> wrapper) {
    return userAccountMapper.selectPage(page, wrapper);
  }

  @Override
  public List<UserAccountDO> list(LambdaQueryWrapper<UserAccountDO> wrapper) {
    return userAccountMapper.selectList(wrapper);
  }

  @Override
  public List<UserAccountDO> listByIds(Collection<String> ids) {
    return userAccountMapper.selectBatchIds(ids);
  }

  @Override
  public long count(LambdaQueryWrapper<UserAccountDO> wrapper) {
    return userAccountMapper.selectCount(wrapper);
  }

  @Override
  public boolean existsByUsername(String username) {
    LambdaQueryWrapper<UserAccountDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserAccountDO::getUsername, username);
    return userAccountMapper.selectCount(wrapper) > 0;
  }

  @Override
  public long countByTenantId(String tenantId) {
    LambdaQueryWrapper<UserAccountDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserAccountDO::getTenantId, tenantId);
    return userAccountMapper.selectCount(wrapper);
  }

  @Override
  public int increaseLoginFailCount(String id, int threshold, int lockMinutes) {
    return userAccountMapper.increaseLoginFailCount(id, threshold, lockMinutes);
  }

  @Override
  public int resetLoginSuccess(String id, String loginIp) {
    return userAccountMapper.resetLoginSuccess(id, loginIp);
  }
}
