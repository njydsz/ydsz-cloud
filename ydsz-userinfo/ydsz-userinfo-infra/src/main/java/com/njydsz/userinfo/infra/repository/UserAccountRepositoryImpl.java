package com.njydsz.userinfo.infra.repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.userinfo.domain.dto.UserAccountDTO;
import com.njydsz.userinfo.domain.enums.EnableStatusEnum;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;
import com.njydsz.userinfo.domain.enums.UserLifecycleStatusEnum;
import com.njydsz.userinfo.domain.query.UserAccountPageQuery;
import com.njydsz.userinfo.domain.repository.UserAccountRepository;
import com.njydsz.userinfo.domain.vo.UserAccountCredentialVO;
import com.njydsz.userinfo.domain.vo.UserAccountVO;
import com.njydsz.userinfo.infra.converter.UserInfoUserConverter;
import com.njydsz.userinfo.infra.entity.UserAccount;
import com.njydsz.userinfo.infra.mapper.UserAccountMapper;

/**
 * 用户账号 Repository 实现
 *
 * <p>基于 MyBatis-Plus 实现 domain 层 {@link UserAccountRepository} 接口。
 * 所有返回值通过 {@link UserInfoUserConverter} 从 DO 转换为 VO，对调用方屏蔽持久化细节。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Repository
@RequiredArgsConstructor
public class UserAccountRepositoryImpl implements UserAccountRepository {

  private final UserAccountMapper userAccountMapper;
  private final UserInfoUserConverter converter;

  @Override
  public Optional<UserAccountVO> findById(String id) {
    UserAccount entity = userAccountMapper.selectById(id);
    return Optional.ofNullable(entity).map(converter::entityToVO);
  }

  @Override
  public Optional<UserAccountVO> findByUsername(String username) {
    LambdaQueryWrapper<UserAccount> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserAccount::getUsername, username);
    UserAccount entity = userAccountMapper.selectOne(wrapper);
    return Optional.ofNullable(entity).map(converter::entityToVO);
  }

  @Override
  public Optional<UserAccountCredentialVO> findCredentialByUsername(String username) {
    LambdaQueryWrapper<UserAccount> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserAccount::getUsername, username);
    UserAccount entity = userAccountMapper.selectOne(wrapper);
    return Optional.ofNullable(entity).map(converter::entityToCredentialVO);
  }

  @Override
  public Optional<UserAccountCredentialVO> findCredentialById(String id) {
    UserAccount entity = userAccountMapper.selectById(id);
    return Optional.ofNullable(entity).map(converter::entityToCredentialVO);
  }

  @Override
  public UserAccountVO save(UserAccountDTO dto) {
    if (dto.getId() == null || dto.getId().isBlank()) {
      // 创建场景
      UserAccount entity = converter.dtoToEntity(dto);
      userAccountMapper.insert(entity);
      return converter.entityToVO(entity);
    } else {
      // 更新场景
      UserAccount entity = converter.dtoToEntityWithId(dto);
      // P1-6: 检查乐观锁冲突（entity.revision 非 null 时 MP 自动带 WHERE revision = ?）
      int affected = userAccountMapper.updateById(entity);
      if (affected == 0) {
        throw new BusinessException(UserInfoExceptionCode.USER_UPDATE_CONFLICT);
      }
      return converter.entityToVO(entity);
    }
  }

  @Override
  public boolean deleteById(String id) {
    return userAccountMapper.deleteById(id) > 0;
  }

  @Override
  public PageResponse<List<UserAccountVO>> page(UserAccountPageQuery query) {
    Page<UserAccount> page = new Page<>(query.getPageNum(), query.getPageSize());
    LambdaQueryWrapper<UserAccount> wrapper = buildWrapper(query);
    Page<UserAccount> result = userAccountMapper.selectPage(page, wrapper);
    List<UserAccountVO> vos = converter.userAccountListToVO(result.getRecords());
    return PageResponse.success(
        result.getTotal(),
        (long) query.getPageNum(),
        (long) query.getPageSize(),
        vos);
  }

  @Override
  public List<UserAccountVO> list(UserAccountPageQuery query) {
    LambdaQueryWrapper<UserAccount> wrapper = buildWrapper(query);
    List<UserAccount> entities = userAccountMapper.selectList(wrapper);
    return converter.userAccountListToVO(entities);
  }

  @Override
  public List<UserAccountVO> listByIds(Collection<String> ids) {
    List<UserAccount> entities = userAccountMapper.selectBatchIds(ids);
    return converter.userAccountListToVO(entities);
  }

  @Override
  public long count(UserAccountPageQuery query) {
    LambdaQueryWrapper<UserAccount> wrapper = buildWrapper(query);
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
  public int increaseLoginFailCount(String id, int threshold, LocalDateTime lockUntil) {
    return userAccountMapper.increaseLoginFailCount(id, threshold, lockUntil);
  }

  @Override
  public int resetLoginSuccess(String id, String loginIp) {
    return userAccountMapper.resetLoginSuccess(id, loginIp);
  }

  @Override
  public int updatePasswordAndResetFailCount(String id, String newPasswordHash) {
    return userAccountMapper.updatePasswordAndResetFailCount(id, newPasswordHash);
  }

  @Override
  public int batchUpdateStatus(Collection<String> ids, EnableStatusEnum status) {
    if (ids == null || ids.isEmpty()) {
      return 0;
    }
    List<String> idList = new ArrayList<>(ids);
    if (status == EnableStatusEnum.ENABLED) {
      return userAccountMapper.batchEnableByIds(idList);
    }
    return userAccountMapper.batchDisableByIds(idList);
  }

  @Override
  public int batchDeleteByIds(Collection<String> ids) {
    if (ids == null || ids.isEmpty()) {
      return 0;
    }
    return userAccountMapper.batchDeleteByIds(new ArrayList<>(ids));
  }

  @Override
  public int updateLifecycleStatus(String id, UserLifecycleStatusEnum status) {
    if (id == null || id.isBlank() || status == null) {
      return 0;
    }
    UpdateWrapper<UserAccount> wrapper = new UpdateWrapper<>();
    wrapper.eq("id", id).set("status", convertLifecycleStatusToString(status));
    return userAccountMapper.update(null, wrapper);
  }

  /**
   * 将 {@link UserLifecycleStatusEnum} 转换为 DB 存储字符串。
   *
   * <p>存储规则：ENABLED → "1"、DISABLED → "0"、PENDING/SUSPENDED/RESIGNED → 枚举名字符串。
   */
  private static String convertLifecycleStatusToString(UserLifecycleStatusEnum status) {
    return switch (status) {
      case ENABLED -> "1";
      case DISABLED -> "0";
      default -> status.name();
    };
  }

  @Override
  public int updateBanFields(
      String id,
      String banType,
      String banReason,
      LocalDateTime banExpireAt,
      String bannedBy) {
    return userAccountMapper.updateBanFields(id, banType, banReason, banExpireAt, bannedBy);
  }

  @Override
  public Optional<UserAccountVO> findByIdWithBan(String id) {
    UserAccount entity = userAccountMapper.selectById(id);
    return Optional.ofNullable(entity).map(converter::entityToVO);
  }

  @Override
  public int unlockAccount(String id) {
    return userAccountMapper.unlockAccount(id);
  }

  @Override
  public Optional<UserAccountVO> findByPhone(String phone) {
    LambdaQueryWrapper<UserAccount> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserAccount::getPhone, phone);
    UserAccount entity = userAccountMapper.selectOne(wrapper);
    return Optional.ofNullable(entity).map(converter::entityToVO);
  }

  @Override
  public Optional<UserAccountVO> findByEmail(String email) {
    LambdaQueryWrapper<UserAccount> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserAccount::getEmail, email);
    UserAccount entity = userAccountMapper.selectOne(wrapper);
    return Optional.ofNullable(entity).map(converter::entityToVO);
  }

  @Override
  public long countLockedUsers() {
    LambdaQueryWrapper<UserAccount> wrapper = new LambdaQueryWrapper<>();
    wrapper.isNotNull(UserAccount::getLockedUntil);
    wrapper.gt(UserAccount::getLockedUntil, LocalDateTime.now());
    return userAccountMapper.selectCount(wrapper);
  }

  @Override
  public long countBannedUsers() {
    LambdaQueryWrapper<UserAccount> wrapper = new LambdaQueryWrapper<>();
    wrapper.isNotNull(UserAccount::getBanType);
    // 永久封禁 或 临时封禁未过期
    wrapper.and(w -> w.eq(UserAccount::getBanType, "PERMANENT")
        .or()
        .eq(UserAccount::getBanType, "TEMPORARY")
        .gt(UserAccount::getBanExpireAt, LocalDateTime.now()));
    return userAccountMapper.selectCount(wrapper);
  }

  @Override
  public List<String> findIdsByBanTypeAndExpireAtBefore(String banType, LocalDateTime expireAt) {
    LambdaQueryWrapper<UserAccount> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserAccount::getBanType, banType);
    wrapper.le(UserAccount::getBanExpireAt, expireAt);
    wrapper.select(UserAccount::getId);
    return userAccountMapper.selectList(wrapper).stream()
        .map(UserAccount::getId)
        .collect(Collectors.toList());
  }

  @Override
  public List<String> findIdsByLockedUntilBefore(LocalDateTime now) {
    LambdaQueryWrapper<UserAccount> wrapper = new LambdaQueryWrapper<>();
    wrapper.isNotNull(UserAccount::getLockedUntil);
    wrapper.le(UserAccount::getLockedUntil, now);
    wrapper.select(UserAccount::getId);
    return userAccountMapper.selectList(wrapper).stream()
        .map(UserAccount::getId)
        .collect(Collectors.toList());
  }

  /**
   * 根据查询参数构建 MyBatis-Plus 查询条件。
   *
   * @param query 分页查询参数
   * @return LambdaQueryWrapper
   */
  private LambdaQueryWrapper<UserAccount> buildWrapper(UserAccountPageQuery query) {
    LambdaQueryWrapper<UserAccount> wrapper = new LambdaQueryWrapper<>();
    applyLikeFilters(wrapper, query);
    applyEqFilters(wrapper, query);
    wrapper.orderByAsc(UserAccount::getCreatedAt);
    return wrapper;
  }

  /**
   * 应用模糊匹配条件（用户名/姓名/手机号/邮箱）。
   *
   * @param wrapper 查询条件构造器
   * @param query 分页查询参数
   */
  private void applyLikeFilters(LambdaQueryWrapper<UserAccount> wrapper,
      UserAccountPageQuery query) {
    if (query.getUsername() != null && !query.getUsername().isBlank()) {
      wrapper.like(UserAccount::getUsername, query.getUsername());
    }
    if (query.getRealName() != null && !query.getRealName().isBlank()) {
      wrapper.like(UserAccount::getRealName, query.getRealName());
    }
    if (query.getPhone() != null && !query.getPhone().isBlank()) {
      wrapper.like(UserAccount::getPhone, query.getPhone());
    }
    if (query.getEmail() != null && !query.getEmail().isBlank()) {
      wrapper.like(UserAccount::getEmail, query.getEmail());
    }
  }

  /**
   * 应用精确匹配条件（状态/用户类型/公司/部门/上级/岗位）。
   *
   * @param wrapper 查询条件构造器
   * @param query 分页查询参数
   */
  private void applyEqFilters(LambdaQueryWrapper<UserAccount> wrapper,
      UserAccountPageQuery query) {
    if (hasText(query.getStatus())) {
      wrapper.eq(UserAccount::getStatus, query.getStatus());
    }
    if (hasText(query.getUserType())) {
      wrapper.eq(UserAccount::getUserType, query.getUserType());
    }
    if (hasText(query.getCompanyId())) {
      wrapper.eq(UserAccount::getCompanyId, query.getCompanyId());
    }
    if (hasText(query.getDeptId())) {
      wrapper.eq(UserAccount::getDeptId, query.getDeptId());
    }
    if (hasText(query.getLeaderId())) {
      wrapper.eq(UserAccount::getLeaderId, query.getLeaderId());
    }
    if (hasText(query.getPositionCode())) {
      wrapper.eq(UserAccount::getPositionCode, query.getPositionCode());
    }
  }

  /**
   * 判断字符串是否非空且非空白。
   *
   * @param value 待判断字符串
   * @return true 表示非空且非空白
   */
  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
