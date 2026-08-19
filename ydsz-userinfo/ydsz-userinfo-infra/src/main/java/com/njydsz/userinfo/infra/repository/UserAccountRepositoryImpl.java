package com.njydsz.userinfo.infra.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.userinfo.domain.dto.UserAccountCreateDTO;
import com.njydsz.userinfo.domain.dto.UserAccountPageQueryDTO;
import com.njydsz.userinfo.domain.dto.UserAccountUpdateDTO;
import com.njydsz.userinfo.domain.enums.EnableStatusEnum;
import com.njydsz.userinfo.domain.repository.UserAccountRepository;
import com.njydsz.userinfo.domain.vo.UserAccountCredentialVO;
import com.njydsz.userinfo.domain.vo.UserAccountVO;
import com.njydsz.userinfo.infra.converter.UserInfoConverter;
import com.njydsz.userinfo.infra.entity.UserAccountDO;
import com.njydsz.userinfo.infra.mapper.UserAccountMapper;

/**
 * 用户账号 Repository 实现
 *
 * <p>基于 MyBatis-Plus 实现 domain 层 {@link UserAccountRepository} 接口。
 * 所有返回值通过 {@link UserInfoConverter} 从 DO 转换为 VO，对调用方屏蔽持久化细节。
 *
 * @author ydsz-team
 * @since 2.18.0
 */
@Repository
@RequiredArgsConstructor
public class UserAccountRepositoryImpl implements UserAccountRepository {

  private final UserAccountMapper userAccountMapper;
  private final UserInfoConverter converter;

  @Override
  public Optional<UserAccountVO> findById(String id) {
    UserAccountDO entity = userAccountMapper.selectById(id);
    return Optional.ofNullable(entity).map(converter::entityToVO);
  }

  @Override
  public Optional<UserAccountVO> findByUsername(String username) {
    LambdaQueryWrapper<UserAccountDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserAccountDO::getUsername, username);
    UserAccountDO entity = userAccountMapper.selectOne(wrapper);
    return Optional.ofNullable(entity).map(converter::entityToVO);
  }

  @Override
  public Optional<UserAccountCredentialVO> findCredentialByUsername(String username) {
    LambdaQueryWrapper<UserAccountDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserAccountDO::getUsername, username);
    UserAccountDO entity = userAccountMapper.selectOne(wrapper);
    return Optional.ofNullable(entity).map(converter::entityToCredentialVO);
  }

  @Override
  public Optional<UserAccountCredentialVO> findCredentialById(String id) {
    UserAccountDO entity = userAccountMapper.selectById(id);
    return Optional.ofNullable(entity).map(converter::entityToCredentialVO);
  }

  @Override
  public UserAccountVO create(UserAccountCreateDTO dto) {
    UserAccountDO entity = converter.createDtoToEntity(dto);
    userAccountMapper.insert(entity);
    return converter.entityToVO(entity);
  }

  @Override
  public UserAccountVO update(UserAccountUpdateDTO dto) {
    UserAccountDO entity = converter.updateDtoToEntity(dto);
    // P1-6: 检查乐观锁冲突（entity.revision 非 null 时 MP 自动带 WHERE revision = ?）
    int affected = userAccountMapper.updateById(entity);
    if (affected == 0) {
      throw new com.njydsz.common.exception.custom.BusinessException(
          com.njydsz.userinfo.domain.enums.UserInfoExceptionCode.USER_UPDATE_CONFLICT);
    }
    return converter.entityToVO(entity);
  }

  @Override
  public boolean deleteById(String id) {
    return userAccountMapper.deleteById(id) > 0;
  }

  @Override
  public PageResponse<List<UserAccountVO>> page(UserAccountPageQueryDTO query) {
    Page<UserAccountDO> page = new Page<>(query.getPageNum(), query.getPageSize());
    LambdaQueryWrapper<UserAccountDO> wrapper = buildWrapper(query);
    Page<UserAccountDO> result = userAccountMapper.selectPage(page, wrapper);
    List<UserAccountVO> vos = converter.userAccountListToVO(result.getRecords());
    return PageResponse.success(
        result.getTotal(),
        (long) query.getPageNum(),
        (long) query.getPageSize(),
        vos);
  }

  @Override
  public List<UserAccountVO> list(UserAccountPageQueryDTO query) {
    LambdaQueryWrapper<UserAccountDO> wrapper = buildWrapper(query);
    List<UserAccountDO> entities = userAccountMapper.selectList(wrapper);
    return converter.userAccountListToVO(entities);
  }

  @Override
  public List<UserAccountVO> listByIds(Collection<String> ids) {
    List<UserAccountDO> entities = userAccountMapper.selectBatchIds(ids);
    return converter.userAccountListToVO(entities);
  }

  @Override
  public long count(UserAccountPageQueryDTO query) {
    LambdaQueryWrapper<UserAccountDO> wrapper = buildWrapper(query);
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

  @Override
  public int updatePasswordAndResetFailCount(String id, String newPasswordHash) {
    return userAccountMapper.updatePasswordAndResetFailCount(id, newPasswordHash);
  }

  @Override
  public int batchUpdateStatus(Collection<String> ids, EnableStatusEnum status) {
    if (ids == null || ids.isEmpty()) {
      return 0;
    }
    List<String> idList = new java.util.ArrayList<>(ids);
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
    return userAccountMapper.batchDeleteByIds(new java.util.ArrayList<>(ids));
  }

  @Override
  public int updateBanFields(
      String id,
      String banType,
      String banReason,
      java.time.LocalDateTime banExpireAt,
      String bannedBy) {
    return userAccountMapper.updateBanFields(id, banType, banReason, banExpireAt, bannedBy);
  }

  @Override
  public Optional<UserAccountVO> findByIdWithBan(String id) {
    UserAccountDO entity = userAccountMapper.selectById(id);
    return Optional.ofNullable(entity).map(converter::entityToVO);
  }

  /**
   * 根据查询参数构建 MyBatis-Plus 查询条件。
   *
   * @param query 分页查询参数
   * @return LambdaQueryWrapper
   */
  private LambdaQueryWrapper<UserAccountDO> buildWrapper(UserAccountPageQueryDTO query) {
    LambdaQueryWrapper<UserAccountDO> wrapper = new LambdaQueryWrapper<>();
    if (query.getUsername() != null && !query.getUsername().isBlank()) {
      wrapper.like(UserAccountDO::getUsername, query.getUsername());
    }
    if (query.getRealName() != null && !query.getRealName().isBlank()) {
      wrapper.like(UserAccountDO::getRealName, query.getRealName());
    }
    if (query.getPhone() != null && !query.getPhone().isBlank()) {
      wrapper.like(UserAccountDO::getPhone, query.getPhone());
    }
    if (query.getEmail() != null && !query.getEmail().isBlank()) {
      wrapper.like(UserAccountDO::getEmail, query.getEmail());
    }
    if (query.getStatus() != null && !query.getStatus().isBlank()) {
      wrapper.eq(UserAccountDO::getStatus, query.getStatus());
    }
    if (query.getUserType() != null && !query.getUserType().isBlank()) {
      wrapper.eq(UserAccountDO::getUserType, query.getUserType());
    }
    if (query.getCompanyId() != null && !query.getCompanyId().isBlank()) {
      wrapper.eq(UserAccountDO::getCompanyId, query.getCompanyId());
    }
    if (query.getDeptId() != null && !query.getDeptId().isBlank()) {
      wrapper.eq(UserAccountDO::getDeptId, query.getDeptId());
    }
    if (query.getLeaderId() != null && !query.getLeaderId().isBlank()) {
      wrapper.eq(UserAccountDO::getLeaderId, query.getLeaderId());
    }
    if (query.getPositionCode() != null && !query.getPositionCode().isBlank()) {
      wrapper.eq(UserAccountDO::getPositionCode, query.getPositionCode());
    }
    wrapper.orderByAsc(UserAccountDO::getCreatedAt);
    return wrapper;
  }
}
