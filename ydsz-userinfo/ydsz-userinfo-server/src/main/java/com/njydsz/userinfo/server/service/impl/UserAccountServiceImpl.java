package com.njydsz.userinfo.server.service.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.auth.annotation.DataScope;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.lock.annotation.YdszDistributedLock;
import com.njydsz.common.search.sync.SearchIndexEventBridge;
import com.njydsz.userinfo.domain.dto.ChangePasswordDTO;
import com.njydsz.userinfo.domain.dto.ResetPasswordDTO;
import com.njydsz.userinfo.domain.dto.UserAccountCreateDTO;
import com.njydsz.userinfo.domain.dto.UserAccountPageQueryDTO;
import com.njydsz.userinfo.domain.dto.UserAccountUpdateDTO;
import com.njydsz.userinfo.domain.dto.UserRoleDTO;
import com.njydsz.userinfo.domain.enums.EnableStatusEnum;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;
import com.njydsz.userinfo.domain.vo.RoleVO;
import com.njydsz.userinfo.domain.vo.UserAccountCredentialVO;
import com.njydsz.userinfo.domain.vo.UserAccountVO;
import com.njydsz.userinfo.domain.vo.UserRoleVO;
import com.njydsz.userinfo.domain.repository.RoleRepository;
import com.njydsz.userinfo.domain.repository.UserAccountRepository;
import com.njydsz.userinfo.domain.repository.UserDeptRepository;
import com.njydsz.userinfo.domain.repository.UserRoleRepository;
import com.njydsz.userinfo.server.auth.AuthService;
import com.njydsz.userinfo.server.auth.PasswordPolicyValidator;
import com.njydsz.userinfo.server.auth.UserPasswordHistoryService;
import com.njydsz.userinfo.server.config.UserInfoProperties;
import com.njydsz.userinfo.server.event.UserDomainEventPublisher;
import com.njydsz.userinfo.server.service.UserAccountService;
import com.njydsz.userinfo.server.service.WorkflowApproverCacheService;

/**
 * 用户账号 Service 实现
 *
 * <p>实现 {@link UserAccountService} 接口，封装用户账号的完整业务逻辑：CRUD、密码管理、角色分配、
 * 审批人展开查询、跨服务名称富化。集成密码 BCrypt 加密、密码策略校验、用户名唯一性校验、
 * 数据权限（{@link DataScope}）、搜索索引同步等横切关注点。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see UserAccountService Service 接口
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserAccountServiceImpl implements UserAccountService {

  /** 用户账号 Repository */
  private final UserAccountRepository userAccountRepository;

  /** 用户-角色关联 Repository */
  private final UserRoleRepository userRoleRepository;

  /** 角色 Repository（用于角色编码查询） */
  private final RoleRepository roleRepository;

  /** 用户-部门关联 Repository */
  private final UserDeptRepository userDeptRepository;

  /** 密码编码器（BCrypt） */
  private final PasswordEncoder passwordEncoder;

  /** 密码策略校验器 */
  private final PasswordPolicyValidator passwordPolicyValidator;

  /** 密码历史服务（用于防止密码重复使用） */
  private final UserPasswordHistoryService passwordHistoryService;

  /** 认证服务（用于改密/禁用时会话驱逐） */
  private final AuthService authService;

  /** 用户中心配置属性 */
  private final UserInfoProperties properties;

  private final ObjectProvider<SearchIndexEventBridge> searchIndexBridgeProvider;

  /** 领域事件发布器（Outbox 模式） */
  private final UserDomainEventPublisher eventPublisher;

  /** 工作流审批人缓存服务（懒加载，避免与 UserAccountService 构造循环依赖） */
  private final ObjectProvider<WorkflowApproverCacheService> workflowCacheProvider;

  /**
   * {@inheritDoc}
   *
   * @throws BusinessException 当用户不存在时抛出
   */
  @Override
  public UserAccountVO getById(String id) {
    return userAccountRepository.findById(id)
        .orElseThrow(() -> new BusinessException(UserInfoExceptionCode.USER_NOT_FOUND));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @DataScope(deptColumn = "dept_id", userColumn = "id")
  public Page<UserAccountVO> page(UserAccountPageQueryDTO query) {
    PageResponse<List<UserAccountVO>> pageResponse = userAccountRepository.page(query);
    Page<UserAccountVO> voPage = new Page<>(
        pageResponse.getPageNum() != null ? pageResponse.getPageNum() : query.getEffectivePageNum(),
        pageResponse.getPageSize() != null ? pageResponse.getPageSize() : query.getEffectivePageSize(),
        pageResponse.getTotal() != null ? pageResponse.getTotal() : 0L);
    voPage.setRecords(pageResponse.getData() != null ? pageResponse.getData() : List.of());
    return voPage;
  }

  /**
   * {@inheritDoc}
   *
   * @return 全部未删除用户列表
   */
  @Override
  @DataScope(deptColumn = "dept_id", userColumn = "id")
  public List<UserAccountVO> list() {
    return userAccountRepository.list(new UserAccountPageQueryDTO());
  }

  /**
   * {@inheritDoc}
   *
   * @throws BusinessException 当 username 已存在或密码不符合策略时抛出
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public String create(UserAccountCreateDTO dto) {
    if (userAccountRepository.existsByUsername(dto.getUsername())) {
      throw new BusinessException(UserInfoExceptionCode.USERNAME_DUPLICATE);
    }

    // 密码策略校验
    passwordPolicyValidator.validate(dto.getPassword(), dto.getUsername());

    // BCrypt 加密密码（DTO 中的 plaintext 密码替换为哈希后写入 DB）
    String passwordHash = passwordEncoder.encode(dto.getPassword());

    // 设置默认值
    if (dto.getStatus() == null) {
      dto.setStatus(EnableStatusEnum.ENABLED);
    }
    if (dto.getTenantId() == null || dto.getTenantId().isBlank()) {
      dto.setTenantId("1");
    }

    UserAccountVO vo = userAccountRepository.create(dto);
    log.info("User created: username={}, id={}", dto.getUsername(), vo.getId());

    // 记录初始密码到密码历史
    passwordHistoryService.recordPasswordHistory(
        vo.getId(), passwordHash, properties.getPasswordHistoryCount());

    indexUpsert(vo);
    eventPublisher.publishUserCreated(vo);
    return vo.getId();
  }

  /**
   * {@inheritDoc}
   *
   * @throws BusinessException 当用户不存在时时抛出
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public boolean update(UserAccountUpdateDTO dto) {
    UserAccountVO existing = userAccountRepository.findById(dto.getId())
        .orElseThrow(() -> new BusinessException(UserInfoExceptionCode.USER_NOT_FOUND));

    UserAccountVO vo = userAccountRepository.update(dto);
    if (vo != null) {
      indexUpsert(vo);
      eventPublisher.publishUserUpdated(vo);
      // 用户被禁用时驱逐全部会话
      if (vo.getStatus() != null && vo.getStatus() == 0) {
        authService.evictAllSessions(dto.getId());
        log.info("User {} disabled, all sessions evicted", dto.getId());
      }
    }
    return vo != null;
  }

  /**
   * {@inheritDoc}
   *
   * @throws BusinessException 当用户不存在时抛出
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public boolean removeById(String id) {
    UserAccountVO existing = userAccountRepository.findById(id)
        .orElseThrow(() -> new BusinessException(UserInfoExceptionCode.USER_NOT_FOUND));

    boolean result = userAccountRepository.deleteById(id);
    if (result) {
      indexDelete(id);
      // 清理密码历史记录（避免敏感数据残留）
      passwordHistoryService.clearHistoryByUserId(id);
      eventPublisher.publishUserDeleted(id, existing.getUsername());
    }
    return result;
  }

  /**
   * {@inheritDoc}
   *
   * @throws BusinessException 当用户不存在、旧密码错误、新旧密码相同、密码不符合策略或与历史密码重复时抛出
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public boolean changePassword(ChangePasswordDTO dto) {
    UserAccountCredentialVO credential = userAccountRepository.findCredentialById(dto.getUserId())
        .orElseThrow(() -> new BusinessException(UserInfoExceptionCode.USER_NOT_FOUND));

    if (!passwordEncoder.matches(dto.getOldPassword(), credential.getPassword())) {
      throw new BusinessException(UserInfoExceptionCode.OLD_PASSWORD_INCORRECT);
    }
    if (passwordEncoder.matches(dto.getNewPassword(), credential.getPassword())) {
      throw new BusinessException(UserInfoExceptionCode.PASSWORD_SAME_AS_OLD);
    }

    // 密码策略校验（含历史密码校验）
    passwordPolicyValidator.validate(
        dto.getNewPassword(), credential.getUsername(), dto.getUserId(), passwordHistoryService);

    String newPasswordHash = passwordEncoder.encode(dto.getNewPassword());

    int affected = userAccountRepository.updatePasswordAndResetFailCount(dto.getUserId(), newPasswordHash);
    boolean result = affected > 0;
    if (result) {
      // 记录新密码到历史
      passwordHistoryService.recordPasswordHistory(
          dto.getUserId(), newPasswordHash, properties.getPasswordHistoryCount());
      // 改密后驱逐该用户全部旧会话，强制重新登录
      authService.evictAllSessions(dto.getUserId());
    }
    return result;
  }

  /**
   * {@inheritDoc}
   *
   * @throws BusinessException 当用户不存在、密码不符合策略或与历史密码重复时抛出
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public boolean resetPassword(ResetPasswordDTO dto) {
    UserAccountCredentialVO credential = userAccountRepository.findCredentialById(dto.getUserId())
        .orElseThrow(() -> new BusinessException(UserInfoExceptionCode.USER_NOT_FOUND));

    // 密码策略校验（含历史密码校验）
    passwordPolicyValidator.validate(
        dto.getNewPassword(), credential.getUsername(), dto.getUserId(), passwordHistoryService);

    String newPasswordHash = passwordEncoder.encode(dto.getNewPassword());

    int affected = userAccountRepository.updatePasswordAndResetFailCount(dto.getUserId(), newPasswordHash);
    boolean result = affected > 0;
    if (result) {
      // 记录新密码到历史
      passwordHistoryService.recordPasswordHistory(
          dto.getUserId(), newPasswordHash, properties.getPasswordHistoryCount());
      // 重置密码后驱逐该用户全部旧会话，强制重新登录
      authService.evictAllSessions(dto.getUserId());
    }
    return result;
  }

  /**
   * {@inheritDoc}
   *
   * <p>先删除旧的用户-角色关联，再批量插入新关联（全量覆盖模式）。
   *
   * <p>P1-7: 加分布式锁防止同一用户并发分配角色导致关联数据丢失。
   *
   * @throws BusinessException 当用户不存在时抛出
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  @YdszDistributedLock(
      key = "'assignRoles:' + #userId",
      waitTime = 3,
      leaseTime = 10,
      message = "该用户的角色分配操作进行中，请稍后重试")
  public boolean assignRoles(String userId, List<String> roleIds) {
    UserAccountVO user = userAccountRepository.findById(userId)
        .orElseThrow(() -> new BusinessException(UserInfoExceptionCode.USER_NOT_FOUND));

    // 清除旧的用户-角色关联
    userRoleRepository.deleteByUserId(userId);

    // 批量插入新关联
    if (roleIds != null && !roleIds.isEmpty()) {
      List<UserRoleDTO> dtoList = new ArrayList<>(roleIds.size());
      for (String roleId : roleIds) {
        UserRoleDTO ur = new UserRoleDTO();
        ur.setUserId(userId);
        ur.setRoleId(roleId);
        dtoList.add(ur);
      }
      userRoleRepository.batchInsert(dtoList);
    }
    log.info("Roles assigned to user {}: count={}", userId, roleIds != null ? roleIds.size() : 0);

    // 角色分配变更会影响 Role:xxx 审批人展开缓存，委托缓存服务失效
    evictWorkflowCache(userId);

    // 角色分配变更后，失效该用户的角色缓存
    authService.evictUserRolesCache(userId);

    // 发布角色变更领域事件（通知 Gateway 刷新权限缓存）
    eventPublisher.publishRoleChanged(userId, roleIds != null ? roleIds.size() : 0);

    return true;
  }

  /**
   * 清理指定用户的工作流审批人缓存。
   *
   * @param userId 用户 ID
   */
  private void evictWorkflowCache(String userId) {
    WorkflowApproverCacheService workflowCache = workflowCacheProvider.getIfAvailable();
    if (workflowCache == null) {
      return;
    }
    try {
      workflowCache.evictRoleCache(null);
      workflowCache.evictUserCache(userId);
    } catch (Exception e) {
      log.warn("Failed to evict workflow cache for user: {}, error: {}", userId, e.getMessage());
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<String> getUserRoleIds(String userId) {
    return userRoleRepository.findRoleIdsByUserId(userId);
  }

  /**
   * 按角色编码查询用户 ID 列表。
   */
  @Override
  public List<String> listUserIdsByRoleCode(String roleCode) {
    if (roleCode == null || roleCode.isBlank()) {
      return Collections.emptyList();
    }
    RoleVO roleVO = roleRepository.findByRoleCode(roleCode).orElse(null);
    if (roleVO == null) {
      return Collections.emptyList();
    }
    return userRoleRepository.findByRoleId(roleVO.getId()).stream()
        .map(UserRoleVO::getUserId)
        .distinct()
        .collect(Collectors.toList());
  }

  /**
   * 查询用户拥有的角色编码列表。
   */
  @Override
  public List<String> listRoleCodesByUserId(String userId) {
    if (userId == null || userId.isBlank()) {
      return Collections.emptyList();
    }
    List<String> roleIds = userRoleRepository.findRoleIdsByUserId(userId);
    if (roleIds.isEmpty()) {
      return Collections.emptyList();
    }
    return roleRepository.listByIds(roleIds).stream()
        .map(RoleVO::getRoleCode)
        .filter(c -> c != null && !c.isBlank())
        .distinct()
        .collect(Collectors.toList());
  }

  /** 查询用户所属部门 ID 列表（支持多部门）。 */
  @Override
  public List<String> listDeptIdsByUserId(String userId) {
    if (userId == null || userId.isBlank()) {
      return Collections.emptyList();
    }
    return userDeptRepository.findDeptIdsByUserId(userId).stream()
        .filter(d -> d != null && !d.isBlank())
        .distinct()
        .collect(Collectors.toList());
  }

  /**
   * 查询用户的直属上级 ID。
   */
  @Override
  public String getLeaderByUserId(String userId) {
    if (userId == null || userId.isBlank()) {
      return null;
    }
    return userAccountRepository.findById(userId)
        .map(UserAccountVO::getLeaderId)
        .orElse(null);
  }

  /**
   * 按岗位编码查询用户 ID 列表。
   */
  @Override
  public List<String> listUserIdsByPositionCode(String positionCode) {
    if (positionCode == null || positionCode.isBlank()) {
      return Collections.emptyList();
    }
    UserAccountPageQueryDTO query = new UserAccountPageQueryDTO();
    query.setPositionCode(positionCode);
    return userAccountRepository.list(query).stream()
        .map(UserAccountVO::getId)
        .distinct()
        .collect(Collectors.toList());
  }

  /**
   * 批量查询用户 ID → 用户真实姓名映射。
   */
  @Override
  public Map<String, String> batchUserNames(Collection<String> userIds) {
    if (userIds == null || userIds.isEmpty()) {
      return Collections.emptyMap();
    }
    List<String> distinctIds =
        userIds.stream()
            .filter(id -> id != null && !id.isBlank())
            .distinct()
            .collect(Collectors.toList());
    if (distinctIds.isEmpty()) {
      return Collections.emptyMap();
    }

    // 限制单次批量查询上限，超出时自动分批
    int batchSize = properties.getBatchSizeLimit();
    if (distinctIds.size() <= batchSize) {
      return batchUserNamesInternal(distinctIds);
    }

    log.warn("Batch user names query exceeds limit: size={}, limit={}", distinctIds.size(), batchSize);
    Map<String, String> result = new LinkedHashMap<>(distinctIds.size());
    List<String> idList = new ArrayList<>(distinctIds);
    for (int i = 0; i < idList.size(); i += batchSize) {
      int end = Math.min(i + batchSize, idList.size());
      List<String> batch = idList.subList(i, end);
      result.putAll(batchUserNamesInternal(batch));
    }
    return result;
  }

  /**
   * 批量查询用户名称（内部方法，不检查上限）。
   */
  private Map<String, String> batchUserNamesInternal(List<String> userIds) {
    List<UserAccountVO> users = userAccountRepository.listByIds(userIds);
    Map<String, String> result = new LinkedHashMap<>(users.size());
    for (UserAccountVO user : users) {
      if (user.getRealName() != null && !user.getRealName().isBlank()) {
        result.put(user.getId(), user.getRealName());
      }
    }
    return result;
  }

  /**
   * {@inheritDoc}
   *
   * <p>批量逻辑删除用户，同时清理密码历史记录和发布删除事件。
   *
   * <p>P0-9: 批量删除改为单条 {@code UPDATE ... SET deleted = 1 WHERE id IN (...)}，
   * 替代逐个 {@code findById} + {@code deleteById} 的 N+1 循环。
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public int batchRemoveByIds(List<String> ids) {
    List<String> distinctIds = distinctNonBlankIds(ids);
    if (distinctIds.isEmpty()) {
      return 0;
    }
    // 单次查询校验存在性并获取用户名（替代逐个 findById）
    Map<String, UserAccountVO> existingMap = collectByIds(distinctIds);
    if (existingMap.size() != distinctIds.size()) {
      throw new BusinessException(UserInfoExceptionCode.USER_NOT_FOUND);
    }
    int affected = userAccountRepository.batchDeleteByIds(distinctIds);
    if (affected > 0) {
      for (UserAccountVO existing : existingMap.values()) {
        indexDelete(existing.getId());
        passwordHistoryService.clearHistoryByUserId(existing.getId());
        eventPublisher.publishUserDeleted(existing.getId(), existing.getUsername());
      }
    }
    return affected;
  }

  /**
   * {@inheritDoc}
   *
   * <p>批量启用用户账号。
   *
   * <p>P0-9: 批量启用改为单条 {@code UPDATE ... SET status = '1' WHERE id IN (...)}，
   * 替代逐个 {@code findById} + {@code update} 的 N+1 循环。
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public int batchEnable(List<String> ids) {
    List<String> distinctIds = distinctNonBlankIds(ids);
    if (distinctIds.isEmpty()) {
      return 0;
    }
    // 单次查询校验存在性并获取用户信息（替代逐个 findById）
    Map<String, UserAccountVO> existingMap = collectByIds(distinctIds);
    if (existingMap.size() != distinctIds.size()) {
      throw new BusinessException(UserInfoExceptionCode.USER_NOT_FOUND);
    }
    int affected = userAccountRepository.batchUpdateStatus(distinctIds, EnableStatusEnum.ENABLED);
    if (affected > 0) {
      for (UserAccountVO vo : existingMap.values()) {
        vo.setStatus(1);
        indexUpsert(vo);
        eventPublisher.publishUserUpdated(vo);
      }
    }
    return affected;
  }

  /**
   * {@inheritDoc}
   *
   * <p>批量禁用用户账号，同时驱逐全部会话。
   *
   * <p>P0-9: 批量禁用改为单条 {@code UPDATE ... SET status = '0' WHERE id IN (...)}，
   * 替代逐个 {@code findById} + {@code update} 的 N+1 循环。
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public int batchDisable(List<String> ids) {
    List<String> distinctIds = distinctNonBlankIds(ids);
    if (distinctIds.isEmpty()) {
      return 0;
    }
    // 单次查询校验存在性并获取用户信息（替代逐个 findById）
    Map<String, UserAccountVO> existingMap = collectByIds(distinctIds);
    if (existingMap.size() != distinctIds.size()) {
      throw new BusinessException(UserInfoExceptionCode.USER_NOT_FOUND);
    }
    int affected = userAccountRepository.batchUpdateStatus(distinctIds, EnableStatusEnum.DISABLED);
    if (affected > 0) {
      for (UserAccountVO vo : existingMap.values()) {
        vo.setStatus(0);
        indexUpsert(vo);
        eventPublisher.publishUserUpdated(vo);
        // 禁用时驱逐全部会话
        authService.evictAllSessions(vo.getId());
      }
    }
    return affected;
  }

  /**
   * 去重并过滤空 ID。
   *
   * @param ids 原始 ID 列表
   * @return 去重后的非空 ID 列表
   */
  private List<String> distinctNonBlankIds(List<String> ids) {
    if (ids == null || ids.isEmpty()) {
      return Collections.emptyList();
    }
    return ids.stream()
        .filter(id -> id != null && !id.isBlank())
        .distinct()
        .collect(Collectors.toList());
  }

  /**
   * 按 ID 集合批量查询用户并转为 Map。
   *
   * @param ids 用户 ID 集合
   * @return userId → UserAccountVO 映射
   */
  private Map<String, UserAccountVO> collectByIds(List<String> ids) {
    return userAccountRepository.listByIds(ids).stream()
        .collect(
            Collectors.toMap(UserAccountVO::getId, vo -> vo, (existing, ignored) -> existing));
  }

  private void indexUpsert(UserAccountVO vo) {
    SearchIndexEventBridge bridge = searchIndexBridgeProvider.getIfAvailable();
    if (bridge != null) {
      bridge.indexUpsert("user", vo);
    }
  }

  private void indexDelete(String id) {
    SearchIndexEventBridge bridge = searchIndexBridgeProvider.getIfAvailable();
    if (bridge != null) {
      bridge.indexDelete("user", id);
    }
  }
}
