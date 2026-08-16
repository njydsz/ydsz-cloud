package com.njydsz.userinfo.infra.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.userinfo.domain.entity.UserAccount;
import com.njydsz.userinfo.domain.repository.UserRepository;
import com.njydsz.userinfo.infra.mapper.UserAccountMapper;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/**
 * UserRepository 的 MyBatis-Plus 实现。
 *
 * <p>位于 infra 层，封装 Mapper 操作，对外暴露领域层语义化接口。 新增时自动生成雪花 ID；更新时按 ID 定位。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

  private final UserAccountMapper userAccountMapper;
  private final SnowflakeIdGenerator snowflakeIdGenerator;

  @Override
  public Optional<UserAccount> findById(String id) {
    if (id == null || id.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(userAccountMapper.selectById(id));
  }

  @Override
  public Optional<UserAccount> findByUsername(String username) {
    if (username == null || username.isBlank()) {
      return Optional.empty();
    }
    LambdaQueryWrapper<UserAccount> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserAccount::getUsername, username);
    return Optional.ofNullable(userAccountMapper.selectOne(wrapper));
  }

  @Override
  public UserAccount save(UserAccount user) {
    if (user == null) {
      throw new IllegalArgumentException("User entity must not be null");
    }
    if (user.getId() == null || user.getId().isBlank()) {
      // 新增：生成 ID
      user.setId(String.valueOf(snowflakeIdGenerator.nextId()));
      userAccountMapper.insert(user);
    } else {
      // 更新：按 ID
      userAccountMapper.updateById(user);
    }
    return user;
  }

  @Override
  public boolean deleteById(String id) {
    if (id == null || id.isBlank()) {
      return false;
    }
    return userAccountMapper.deleteById(id) > 0;
  }

  @Override
  public boolean existsByUsername(String username) {
    if (username == null || username.isBlank()) {
      return false;
    }
    LambdaQueryWrapper<UserAccount> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserAccount::getUsername, username);
    return userAccountMapper.exists(wrapper);
  }

  @Override
  public boolean existsByUsernameAndNotId(String username, String excludeUserId) {
    if (username == null || username.isBlank()) {
      return false;
    }
    LambdaQueryWrapper<UserAccount> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserAccount::getUsername, username);
    if (excludeUserId != null) {
      wrapper.ne(UserAccount::getId, excludeUserId);
    }
    return userAccountMapper.exists(wrapper);
  }

  @Override
  public List<UserAccount> findByIds(List<String> ids) {
    if (ids == null || ids.isEmpty()) {
      return Collections.emptyList();
    }
    return userAccountMapper.selectBatchIds(ids);
  }

  @Override
  public long count(String status) {
    LambdaQueryWrapper<UserAccount> wrapper = new LambdaQueryWrapper<>();
    if (status != null) {
      wrapper.eq(UserAccount::getStatus, status);
    }
    return userAccountMapper.selectCount(wrapper);
  }
}
