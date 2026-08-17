package com.njydsz.userinfo.infra.repository.impl;

import java.util.Collection;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.userinfo.domain.entity.UserAccount;
import com.njydsz.userinfo.infra.mapper.UserAccountMapper;
import com.njydsz.userinfo.infra.repository.UserAccountRepository;

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
  public UserAccount findById(String id) {
    return userAccountMapper.selectById(id);
  }

  @Override
  public UserAccount findByUsername(String username) {
    LambdaQueryWrapper<UserAccount> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserAccount::getUsername, username);
    return userAccountMapper.selectOne(wrapper);
  }

  @Override
  public UserAccount save(UserAccount entity) {
    if (entity.getId() == null || entity.getId().isBlank()) {
      userAccountMapper.insert(entity);
    } else {
      userAccountMapper.updateById(entity);
    }
    return entity;
  }

  @Override
  public int insert(UserAccount entity) {
    return userAccountMapper.insert(entity);
  }

  @Override
  public int updateById(UserAccount entity) {
    return userAccountMapper.updateById(entity);
  }

  @Override
  public int deleteById(String id) {
    return userAccountMapper.deleteById(id);
  }

  @Override
  public Page<UserAccount> page(Page<UserAccount> page, LambdaQueryWrapper<UserAccount> wrapper) {
    return userAccountMapper.selectPage(page, wrapper);
  }

  @Override
  public List<UserAccount> list(LambdaQueryWrapper<UserAccount> wrapper) {
    return userAccountMapper.selectList(wrapper);
  }

  @Override
  public List<UserAccount> listByIds(Collection<String> ids) {
    return userAccountMapper.selectBatchIds(ids);
  }

  @Override
  public long count(LambdaQueryWrapper<UserAccount> wrapper) {
    return userAccountMapper.selectCount(wrapper);
  }

  @Override
  public boolean existsByUsername(String username) {
    LambdaQueryWrapper<UserAccount> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserAccount::getUsername, username);
    return userAccountMapper.selectCount(wrapper) > 0;
  }

  @Override
  public long countByTenantId(String tenantId) {
    LambdaQueryWrapper<UserAccount> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserAccount::getTenantId, tenantId);
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
