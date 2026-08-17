package com.njydsz.userinfo.server.service.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.auth.annotation.DataScope;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.search.sync.SearchIndexEventBridge;
import com.njydsz.common.util.bean.BeanUpdateUtil;
import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.userinfo.infra.converter.UserInfoConverter;
import com.njydsz.userinfo.domain.dto.ChangePasswordDTO;
import com.njydsz.userinfo.domain.dto.ResetPasswordDTO;
import com.njydsz.userinfo.domain.dto.UserAccountCreateDTO;
import com.njydsz.userinfo.domain.dto.UserAccountPageQueryDTO;
import com.njydsz.userinfo.domain.dto.UserAccountUpdateDTO;
import com.njydsz.userinfo.infra.entity.UserAccountDO;
import com.njydsz.userinfo.infra.entity.UserDeptDO;
import com.njydsz.userinfo.infra.entity.UserRoleDO;
import com.njydsz.userinfo.domain.enums.EnableStatusEnum;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;
import com.njydsz.userinfo.domain.vo.UserAccountVO;
import com.njydsz.userinfo.infra.repository.RoleRepository;
import com.njydsz.userinfo.infra.repository.UserAccountRepository;
import com.njydsz.userinfo.infra.repository.UserDeptRepository;
import com.njydsz.userinfo.infra.repository.UserRoleRepository;
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
 * <p>实现 {@link UserAccountService} 接口，封装用户账号的完整业务逻辑：CRUD、密码管理、角色分配、 审批人展开查询、跨服务名称富化。集成密码 BCrypt
 * 加密、密码策略校验、用户名唯一性校验、 数据权限（{@link DataScope}）、搜索索引同步等横切关注点。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li>用户 CRUD（含密码 BCrypt 加密存储）
 *   <li>密码管理（用户自助修改 / 管理员重置，含 {@link PasswordPolicyValidator} 策略校验）
 *   <li>角色分配（覆盖式：清空旧关联 + 批量插入新关联）
 *   <li>审批人展开查询（{@code listUserIdsByRoleCode} / {@code listUserIdsByPositionCode} / {@code
 *       getLeaderByUserId} / {@code listDeptIdsByUserId}，供工作流 Feign 调用）
 *   <li>跨服务名称富化（{@code batchUserNames}，供 NameAssembler 调用）
 *   <li>数据权限隔离（{@code @DataScope} 自动追加部门过滤）
 *   <li>搜索索引同步（{@link SearchIndexEventBridge} 异步 upsert/delete）
 * </ul>
 *
 * <p><b>安全设计：</b>
 *
 * <ul>
 *   <li>密码字段全程不进入 VO/响应（{@code UserInfoConverter} 自动脱敏）
 *   <li>BCrypt cost 由 {@code ydsz.system.app.bcrypt-strength} 配置（默认 10）
 *   <li>密码策略：长度、复杂度、历史密码去重由 {@link PasswordPolicyValidator} 校验
 *   <li>登录失败保护：{@code loginFailCount} 达到阈值时设置 {@code lockedUntil}，定时解锁
 * </ul>
 *
 * <p><b>事务：</b>所有写操作（{@code create/update/removeById/changePassword/resetPassword/assignRoles}） 开启
 * {@code @Transactional(rollbackFor = Exception.class)}，确保任一异常触发完整回滚。
 *
 * <p><b>性能：</b>
 *
 * <ul>
 *   <li>{@link #page} 与 {@link #list} 均启用 {@link DataScope}，数据权限自动追加 WHERE 条件
 *   <li>{@link #assignRoles} 使用批量插入（{@code userRoleRepository.batchInsert}）避免 N+1
 *   <li>{@link #batchUserNames} 使用单条 {@code IN} 查询，单次往返
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see UserAccountService Service 接口
 * @see UserAccountDO 用户实体
 * @see com.njydsz.userinfo.web.controller.UserAccountController 用户 Controller
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserAccountServiceImpl implements UserAccountService {

  /** 分布式 ID 生成器 */
  private final SnowflakeIdGenerator snowflakeIdGenerator;

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
    UserAccountDO entity = userAccountRepository.findById(id);
    if (entity == null) {
      throw new BusinessException(UserInfoExceptionCode.USER_NOT_FOUND);
    }
    return UserInfoConverter.INSTANT.entityToVO(entity);
  }

  /**
   * {@inheritDoc}
   *
   * <p>支持按 username/realName/phone/email 模糊匹配、status/userType/companyId 精确匹配过滤。
   *
   * @param query 分页查询参数
   * @return 分页结果
   */
  @Override
  @DataScope(deptColumn = "dept_id", userColumn = "id")
  public Page<UserAccountVO> page(UserAccountPageQueryDTO query) {
    Page<UserAccountDO> page = new Page<>(query.getEffectivePageNum(), query.getEffectivePageSize());
    LambdaQueryWrapper<UserAccountDO> wrapper = buildPageQueryWrapper(query);
    wrapper.orderByDesc(UserAccountDO::getCreatedAt);

    Page<UserAccountDO> result = userAccountRepository.page(page, wrapper);
    Page<UserAccountVO> voPage =
        new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
    List<UserAccountVO> voList =
        result.getRecords().stream()
            .map(UserInfoConverter.INSTANT::entityToVO)
            .collect(Collectors.toList());
    voPage.setRecords(voList);
    return voPage;
  }

  /**
   * 构建分页查询条件（从 DTO 提取 LIKE/EQ 条件）。
   *
   * @param query 分页查询参数
   * @return 填充好条件的 QueryWrapper
   */
  private LambdaQueryWrapper<UserAccountDO> buildPageQueryWrapper(UserAccountPageQueryDTO query) {
    LambdaQueryWrapper<UserAccountDO> wrapper = new LambdaQueryWrapper<>();

    applyLikeIfPresent(wrapper, UserAccountDO::getUsername, query.getUsername());
    applyLikeIfPresent(wrapper, UserAccountDO::getRealName, query.getRealName());
    applyLikeIfPresent(wrapper, UserAccountDO::getPhone, query.getPhone());
    applyLikeIfPresent(wrapper, UserAccountDO::getEmail, query.getEmail());
    applyStatusIfPresent(wrapper, query.getStatus());
    applyEqIfPresent(wrapper, UserAccountDO::getUserType, query.getUserType());
    applyEqIfPresent(wrapper, UserAccountDO::getCompanyId, query.getCompanyId());
    return wrapper;
  }

  /**
   * 当状态非空时，将字符串状态转换为枚举后应用 EQ 条件。
   *
   * @param wrapper QueryWrapper
   * @param status  状态字符串（{@code "ENABLED"}/{@code "DISABLED"}，可为 null）
   */
  private void applyStatusIfPresent(LambdaQueryWrapper<UserAccountDO> wrapper, String status) {
    if (status != null && !status.isBlank()) {
      EnableStatusEnum statusEnum = EnableStatusEnum.parse(status);
      if (statusEnum != null) {
        wrapper.eq(UserAccountDO::getStatus, statusEnum);
      }
    }
  }

  /**
   * 当值非空非空白时，应用 LIKE 条件。
   *
   * @param wrapper QueryWrapper
   * @param column 实体字段 getter 方法引用
   * @param value 查询值（可为 null 或空白）
   */
  private void applyLikeIfPresent(
      LambdaQueryWrapper<UserAccountDO> wrapper, SFunction<UserAccountDO, String> column, String value) {
    if (value != null && !value.isBlank()) {
      wrapper.like(column, value);
    }
  }

  /**
   * 当值非空非空白时，应用 EQ 条件。
   *
   * @param wrapper QueryWrapper
   * @param column 实体字段 getter 方法引用
   * @param value 查询值（可为 null 或空白）
   */
  private void applyEqIfPresent(
      LambdaQueryWrapper<UserAccountDO> wrapper, SFunction<UserAccountDO, String> column, String value) {
    if (value != null && !value.isBlank()) {
      wrapper.eq(column, value);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @return 全部未删除用户列表（按创建时间降序）
   */
  @Override
  @DataScope(deptColumn = "dept_id", userColumn = "id")
  public List<UserAccountVO> list() {
    LambdaQueryWrapper<UserAccountDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.orderByDesc(UserAccountDO::getCreatedAt);
    return userAccountRepository.list(wrapper).stream()
        .map(UserInfoConverter.INSTANT::entityToVO)
        .collect(Collectors.toList());
  }

  /**
   * {@inheritDoc}
   *
   * <p>执行 username 唯一性校验 + 密码策略校验，BCrypt 加密存储密码， status 默认 "1"（启用），tenantId 为空时默认 "1"。
   *
   * <p>创建成功后记录初始密码到密码历史表。
   *
   * @throws BusinessException 当 username 已存在或密码不符合策略时抛出
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public String create(UserAccountCreateDTO dto) {
    LambdaQueryWrapper<UserAccountDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserAccountDO::getUsername, dto.getUsername());
    if (userAccountRepository.count(wrapper) > 0) {
      throw new BusinessException(UserInfoExceptionCode.USERNAME_DUPLICATE);
    }

    // 密码策略校验
    passwordPolicyValidator.validate(dto.getPassword(), dto.getUsername());

    UserAccountDO entity = UserInfoConverter.INSTANT.createDtoToEntity(dto);
    String passwordHash = passwordEncoder.encode(dto.getPassword());
    entity.setPassword(passwordHash);
    entity.setStatusEnum(EnableStatusEnum.ENABLED);
    entity.setLoginFailCount(0);
    if (dto.getTenantId() == null || dto.getTenantId().isBlank()) {
      entity.setTenantId("1");
    }
    userAccountRepository.insert(entity);
    log.info("User created: username={}, id={}", entity.getUsername(), entity.getId());

    // 记录初始密码到密码历史
    passwordHistoryService.recordPasswordHistory(
        entity.getId(), passwordHash, properties.getPasswordHistoryCount());

    indexUpsert(entity);
    eventPublisher.publishUserCreated(entity);
    return entity.getId();
  }

  /**
   * {@inheritDoc}
   *
   * <p>使用 MapStruct 转换（更新操作使用 BeanUpdateUtil 动态复制非 null 字段） status 字段从 Integer 转为 String 存储。
   *
   * @throws BusinessException 当用户不存在或已删除时抛出
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public boolean update(UserAccountUpdateDTO dto) {
    UserAccountDO entity = userAccountRepository.findById(dto.getId());
    if (entity == null || entity.getDeleted() == 1) {
      throw new BusinessException(UserInfoExceptionCode.USER_NOT_FOUND);
    }
    // 仅复制非 null 属性，避免覆盖已有值；额外忽略 id（主键不可变）
    BeanUpdateUtil.copyNonNull(dto, entity, "id");
    if (dto.getStatus() != null) {
      entity.setStatusEnum(dto.getStatus());
    }
    boolean result = userAccountRepository.updateById(entity) > 0;
    if (result) {
      indexUpsert(entity);
      eventPublisher.publishUserUpdated(entity);
      // P1-1: 用户被禁用时驱逐全部会话
      if (entity.getStatusEnum() == EnableStatusEnum.DISABLED) {
        authService.evictAllSessions(dto.getId());
        log.info("User {} disabled, all sessions evicted", dto.getId());
      }
    }
    return result;
  }

  /**
   * {@inheritDoc}
   *
   * @throws BusinessException 当用户不存在或已删除时抛出
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public boolean removeById(String id) {
    UserAccountDO entity = userAccountRepository.findById(id);
    if (entity == null || entity.getDeleted() == 1) {
      throw new BusinessException(UserInfoExceptionCode.USER_NOT_FOUND);
    }
    boolean result = userAccountRepository.deleteById(id) > 0;
    if (result) {
      indexDelete(id);
      // 清理密码历史记录（避免敏感数据残留）
      passwordHistoryService.clearHistoryByUserId(id);
      eventPublisher.publishUserDeleted(id, entity.getUsername());
    }
    return result;
  }

  /**
   * {@inheritDoc}
   *
   * <p>校验旧密码 → 新旧密码不能相同 → 密码策略校验（含历史密码校验）→ BCrypt 加密存储。
   *
   * <p>修改成功后将新密码记录到历史表。
   *
   * @throws BusinessException 当用户不存在、旧密码错误、新旧密码相同、密码不符合策略或与历史密码重复时抛出
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public boolean changePassword(ChangePasswordDTO dto) {
    UserAccountDO entity = userAccountRepository.findById(dto.getUserId());
    if (entity == null || entity.getDeleted() == 1) {
      throw new BusinessException(UserInfoExceptionCode.USER_NOT_FOUND);
    }
    if (!passwordEncoder.matches(dto.getOldPassword(), entity.getPassword())) {
      throw new BusinessException(UserInfoExceptionCode.OLD_PASSWORD_INCORRECT);
    }
    if (passwordEncoder.matches(dto.getNewPassword(), entity.getPassword())) {
      throw new BusinessException(UserInfoExceptionCode.PASSWORD_SAME_AS_OLD);
    }

    // 密码策略校验（含历史密码校验）
    passwordPolicyValidator.validate(
        dto.getNewPassword(), entity.getUsername(), dto.getUserId(), passwordHistoryService);

    String newPasswordHash = passwordEncoder.encode(dto.getNewPassword());
    entity.setPassword(newPasswordHash);

    boolean result = userAccountRepository.updateById(entity) > 0;
    if (result) {
      // 记录新密码到历史
      passwordHistoryService.recordPasswordHistory(
          dto.getUserId(), newPasswordHash, properties.getPasswordHistoryCount());
      // P1-1: 改密后驱逐该用户全部旧会话，强制重新登录
      authService.evictAllSessions(dto.getUserId());
    }
    return result;
  }

  /**
   * {@inheritDoc}
   *
   * <p>密码策略校验 → BCrypt 加密存储 → 重置失败计数和锁定状态。
   *
   * <p>重置成功后将新密码记录到历史表。
   *
   * @throws BusinessException 当用户不存在、密码不符合策略或与历史密码重复时抛出
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public boolean resetPassword(ResetPasswordDTO dto) {
    UserAccountDO entity = userAccountRepository.findById(dto.getUserId());
    if (entity == null || entity.getDeleted() == 1) {
      throw new BusinessException(UserInfoExceptionCode.USER_NOT_FOUND);
    }

    // 密码策略校验（含历史密码校验）
    passwordPolicyValidator.validate(
        dto.getNewPassword(), entity.getUsername(), dto.getUserId(), passwordHistoryService);

    String newPasswordHash = passwordEncoder.encode(dto.getNewPassword());
    entity.setPassword(newPasswordHash);
    entity.setLoginFailCount(0);
    entity.setLockedUntil(null);

    boolean result = userAccountRepository.updateById(entity) > 0;
    if (result) {
      // 记录新密码到历史
      passwordHistoryService.recordPasswordHistory(
          dto.getUserId(), newPasswordHash, properties.getPasswordHistoryCount());
      // P1-1: 重置密码后驱逐该用户全部旧会话，强制重新登录
      authService.evictAllSessions(dto.getUserId());
    }
    return result;
  }

  /**
   * {@inheritDoc}
   *
   * <p>先删除旧的用户-角色关联，再批量插入新关联（全量覆盖模式）。
   *
   * <p>分配成功后清理相关的工作流审批人缓存。
   *
   * @throws BusinessException 当用户不存在时抛出
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public boolean assignRoles(String userId, List<String> roleIds) {
    UserAccountDO entity = userAccountRepository.findById(userId);
    if (entity == null || entity.getDeleted() == 1) {
      throw new BusinessException(UserInfoExceptionCode.USER_NOT_FOUND);
    }

    LambdaQueryWrapper<UserRoleDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserRoleDO::getUserId, userId);
    userRoleRepository.delete(wrapper);

    // 批量插入（替代 N+1 循环）
    List<UserRoleDO> list = new ArrayList<>(roleIds.size());
    for (String roleId : roleIds) {
      UserRoleDO ur = new UserRoleDO();
      ur.setId(String.valueOf(snowflakeIdGenerator.nextId()));
      ur.setUserId(userId);
      ur.setRoleId(roleId);
      ur.setTenantId(entity.getTenantId());
      list.add(ur);
    }
    if (!list.isEmpty()) {
      userRoleRepository.batchInsert(list);
    }
    log.info("Roles assigned to user {}: {}", userId, roleIds);

    // P0-2: 角色分配变更会影响 RoleDO:xxx 审批人展开缓存，按角色编码逐个失效。
    // 原实现仅删除 leader 缓存（key 维度错误），导致 RoleDO:xxx 旧名单残留。
    evictWorkflowCache(userId);

    // P1-1: 角色分配变更后，失效该用户的角色缓存
    authService.evictUserRolesCache(userId);

    // 发布角色变更领域事件（通知 Gateway 刷新权限缓存）
    eventPublisher.publishRoleChanged(userId, roleIds.size());

    return true;
  }

  /**
   * 清理指定用户的工作流审批人缓存。
   *
   * <p>角色分配变更会同时影响「角色→用户列表」与「用户→上级」两类缓存， 这里统一委托 {@link WorkflowApproverCacheService} 处理，避免硬编码缓存
   * key。
   *
   * @param userId 用户 ID
   */
  private void evictWorkflowCache(String userId) {
    WorkflowApproverCacheService workflowCache = workflowCacheProvider.getIfAvailable();
    if (workflowCache == null) {
      return;
    }
    try {
      // 角色分配变更后，全部 RoleDO:xxx 缓存都可能过期（角色成员已变化），全量失效最安全
      workflowCache.evictRoleCache(null);
      // 用户角色变化不影响 leader，但用户可能同时被移出审批链，连带失效 leader 缓存
      workflowCache.evictUserCache(userId);
    } catch (Exception e) {
      log.warn("Failed to evict workflow cache for user: {}, error: {}", userId, e.getMessage());
    }
  }

  /**
   * {@inheritDoc}
   *
   * @param userId 用户 ID
   * @return 角色 ID 列表
   */
  @Override
  public List<String> getUserRoleIds(String userId) {
    return userRoleRepository.findRoleIdsByUserId(userId);
  }

  /**
   * 按角色编码查询用户 ID 列表。
   *
   * <p>实现：先按 role_code 查 ydsz_role 获取 role_id，再按 role_id 查 ydsz_user_role 获取 user_id 列表。
   * 因单次查询数据量可控（单角色关联用户通常 ≤ 千级），未做缓存。
   */
  @Override
  public List<String> listUserIdsByRoleCode(String roleCode) {
    if (roleCode == null || roleCode.isBlank()) {
      return Collections.emptyList();
    }
    com.njydsz.userinfo.infra.entity.RoleDO RoleDO = roleRepository.findByRoleCode(roleCode);
    if (RoleDO == null) {
      return Collections.emptyList();
    }
    LambdaQueryWrapper<UserRoleDO> userRoleWrapper = new LambdaQueryWrapper<>();
    userRoleWrapper.eq(UserRoleDO::getRoleId, RoleDO.getId());
    return userRoleRepository.list(userRoleWrapper).stream()
        .map(UserRoleDO::getUserId)
        .distinct()
        .collect(Collectors.toList());
  }

  /**
   * 查询用户拥有的角色编码列表。
   *
   * <p>实现：先按 user_id 查 ydsz_user_role 获取 role_id 列表，再按 role_id IN(...) 查 ydsz_role 获取 role_code。
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
    LambdaQueryWrapper<com.njydsz.userinfo.infra.entity.RoleDO> roleWrapper = new LambdaQueryWrapper<>();
    roleWrapper.in(com.njydsz.userinfo.infra.entity.RoleDO::getId, roleIds);
    return roleRepository.list(roleWrapper).stream()
        .map(com.njydsz.userinfo.infra.entity.RoleDO::getRoleCode)
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
   *
   * <p>实现：直接读 ydsz_user_account.leader_id 字段。
   */
  @Override
  public String getLeaderByUserId(String userId) {
    if (userId == null || userId.isBlank()) {
      return null;
    }
    UserAccountDO entity = userAccountRepository.findById(userId);
    if (entity == null || entity.getDeleted() == 1) {
      return null;
    }
    return entity.getLeaderId();
  }

  /**
   * 按岗位编码查询用户 ID 列表。
   *
   * <p>实现：直接按 position_code 查 ydsz_user_account。
   */
  @Override
  public List<String> listUserIdsByPositionCode(String positionCode) {
    if (positionCode == null || positionCode.isBlank()) {
      return Collections.emptyList();
    }
    LambdaQueryWrapper<UserAccountDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserAccountDO::getPositionCode, positionCode);
    return userAccountRepository.list(wrapper).stream()
        .map(UserAccountDO::getId)
        .distinct()
        .collect(Collectors.toList());
  }

  /**
   * 批量查询用户 ID → 用户真实姓名映射。
   *
   * <p>实现：单条 SQL 完成（已自动追加 {@code deleted = 0} 条件）。
   *
   * <p>返回 realName（而非 username）：富化场景需要展示给人看的是真实姓名。 若 realName 为空则该 userId 不出现在结果中（让 NameAssembler
   * 兜底用 userId 顶替）。
   *
   * <p>P1-2: 单次批量查询上限 500，超出时自动分批执行，避免巨型 IN 查询导致数据库性能下降。
   *
   * @param userIds 用户 ID 集合
   * @return userId → realName 映射
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

    // P1-2: 限制单次批量查询上限，超出时自动分批
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
   *
   * @param userIds 用户 ID 列表（已去重、已过滤空值）
   * @return userId → realName 映射
   */
  private Map<String, String> batchUserNamesInternal(List<String> userIds) {
    List<UserAccountDO> users = userAccountRepository.listByIds(userIds);
    Map<String, String> result = new LinkedHashMap<>(users.size());
    for (UserAccountDO user : users) {
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
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public int batchRemoveByIds(List<String> ids) {
    if (ids == null || ids.isEmpty()) {
      return 0;
    }
    int count = 0;
    for (String id : ids) {
      UserAccountDO entity = userAccountRepository.findById(id);
      if (entity == null || entity.getDeleted() == 1) {
        throw new BusinessException(UserInfoExceptionCode.USER_NOT_FOUND);
      }
      if (userAccountRepository.deleteById(id) > 0) {
        indexDelete(id);
        passwordHistoryService.clearHistoryByUserId(id);
        eventPublisher.publishUserDeleted(id, entity.getUsername());
        count++;
      }
    }
    return count;
  }

  /**
   * {@inheritDoc}
   *
   * <p>批量启用用户账号，同时驱逐全部会话。
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public int batchEnable(List<String> ids) {
    if (ids == null || ids.isEmpty()) {
      return 0;
    }
    int count = 0;
    for (String id : ids) {
      UserAccountDO entity = userAccountRepository.findById(id);
      if (entity == null || entity.getDeleted() == 1) {
        throw new BusinessException(UserInfoExceptionCode.USER_NOT_FOUND);
      }
      entity.enable();
      if (userAccountRepository.updateById(entity) > 0) {
        indexUpsert(entity);
        eventPublisher.publishUserUpdated(entity);
        count++;
      }
    }
    return count;
  }

  /**
   * {@inheritDoc}
   *
   * <p>批量禁用用户账号，同时驱逐全部会话。
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public int batchDisable(List<String> ids) {
    if (ids == null || ids.isEmpty()) {
      return 0;
    }
    int count = 0;
    for (String id : ids) {
      UserAccountDO entity = userAccountRepository.findById(id);
      if (entity == null || entity.getDeleted() == 1) {
        throw new BusinessException(UserInfoExceptionCode.USER_NOT_FOUND);
      }
      entity.disable();
      if (userAccountRepository.updateById(entity) > 0) {
        indexUpsert(entity);
        eventPublisher.publishUserUpdated(entity);
        // 禁用时驱逐全部会话
        authService.evictAllSessions(id);
        count++;
      }
    }
    return count;
  }

  private void indexUpsert(UserAccountDO entity) {
    SearchIndexEventBridge bridge = searchIndexBridgeProvider.getIfAvailable();
    if (bridge != null) {
      bridge.indexUpsert("user", entity);
    }
  }

  private void indexDelete(String id) {
    SearchIndexEventBridge bridge = searchIndexBridgeProvider.getIfAvailable();
    if (bridge != null) {
      bridge.indexDelete("user", id);
    }
  }
}
