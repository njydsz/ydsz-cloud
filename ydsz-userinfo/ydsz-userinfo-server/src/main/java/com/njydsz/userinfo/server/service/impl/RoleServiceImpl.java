package com.njydsz.userinfo.server.service.impl;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.auth.annotation.DataScope;
import com.njydsz.common.auth.event.PermissionChangeNotifier;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.userinfo.domain.dto.RoleDTO;
import com.njydsz.userinfo.domain.dto.RolePermissionDTO;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;
import com.njydsz.userinfo.domain.query.RolePageQuery;
import com.njydsz.userinfo.domain.repository.RolePermissionRepository;
import com.njydsz.userinfo.domain.repository.RoleRepository;
import com.njydsz.userinfo.domain.repository.UserRoleRepository;
import com.njydsz.userinfo.domain.vo.RoleVO;
import com.njydsz.userinfo.server.auth.DbRolePermissionLoader;
import com.njydsz.userinfo.server.event.UserDomainEventPublisher;
import com.njydsz.userinfo.server.service.RoleService;

/**
 * 角色 Service 实现
 *
 * <p>实现 {@link RoleService} 接口，封装角色的完整业务逻辑：CRUD、roleCode 唯一性校验、 内置角色保护、角色-权限批量分配、跨服务名称富化。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li>角色 CRUD（含 roleCode 唯一性校验）
 *   <li>内置角色保护（{@code builtIn=true} 禁止删除，{@code roleCode} 不可修改）
 *   <li>删除前置校验（仍有用户关联时禁止删除，避免悬挂引用）
 *   <li>角色-权限分配（覆盖式：清空旧关联 + 批量插入新关联）
 *   <li>跨服务名称富化（{@code batchNamesByIds}，供 NameAssembler 调用）
 *   <li>数据权限隔离（{@code @DataScope} 自动追加创建人部门过滤）
 * </ul>
 *
 * <p><b>事务：</b>所有写操作（{@code create/update/removeById/assignPermissions}） 开启
 * {@code @Transactional(rollbackFor = Exception.class)}，确保任一异常触发完整回滚。
 *
 * <p><b>性能：</b>
 *
 * <ul>
 *   <li>{@link #assignPermissions} 使用批量插入（{@code rolePermissionRepository.batchInsert}）避免 N+1
 *   <li>{@link #batchNamesByIds} 使用单条 {@code IN} 查询，单次往返
 *   <li>分页与列表查询均按 {@code sortOrder} 升序，匹配前端展示顺序
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see RoleService Service 接口
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

  /** 角色权限缓存 Redis key 前缀 */
  private static final String CACHE_KEY_ROLE_PERMISSIONS_PREFIX = "userinfo:Role:permissions:";

  /** 角色权限缓存过期时间（秒）：10 分钟 */
  private static final long CACHE_TTL_ROLE_PERMISSIONS = 600;

  /** 分布式 ID 生成器 */
  private final SnowflakeIdGenerator snowflakeIdGenerator;

  /** 角色 Repository */
  private final RoleRepository roleRepository;

  /** 角色-权限关联 Repository */
  private final RolePermissionRepository rolePermissionRepository;

  /** 用户-角色关联 Repository（用于删除前检查是否有用户关联） */
  private final UserRoleRepository userRoleRepository;

  /** Redis 服务 */
  private final RedisStringOps redisStringOps;

  /** 领域事件发布器 */
  private final UserDomainEventPublisher eventPublisher;

  /** 权限变更事件发布器（common-auth，通知 Gateway 等节点刷新权限缓存） */
  private final PermissionChangeNotifier permissionChangeNotifier;

  /** 角色权限 DB 结果缓存加载器（角色/权限变更时按角色编码失效） */
  private final DbRolePermissionLoader permissionLoader;

  /**
   * {@inheritDoc}
   *
   * @throws BusinessException 当角色不存在时抛出
   */
  @Override
  public RoleVO getById(String id) {
    return roleRepository.findById(id)
        .orElseThrow(() -> new BusinessException(UserInfoExceptionCode.ROLE_NOT_FOUND));
  }

  /**
   * {@inheritDoc}
   *
   * <p>支持按 roleCode/roleName 模糊匹配、status 精确匹配过滤，结果按 sortOrder 升序。
   */
  @Override
  @DataScope(deptColumn = "dept_id", userColumn = "created_by")
  public PageResponse<List<RoleVO>> page(RolePageQuery query) {
    return roleRepository.page(query);
  }

  /**
   * {@inheritDoc}
   *
   * @return 全部未删除角色列表（按 sortOrder 升序）
   */
  @Override
  @DataScope(deptColumn = "dept_id", userColumn = "created_by")
  public List<RoleVO> list() {
    return roleRepository.list(new RolePageQuery());
  }

  /**
   * {@inheritDoc}
   *
   * <p>执行 roleCode 唯一性校验后插入，status 默认 ENABLED，builtIn 默认 false。
   *
   * <p>创建后无需失效角色-权限缓存（新角色无权限分配）。
   *
   * @throws BusinessException 当 roleCode 已存在时抛出
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public String create(RoleDTO dto) {
    RolePageQuery query = new RolePageQuery();
    query.setRoleCode(dto.getRoleCode());
    if (roleRepository.countByQuery(query) > 0) {
      throw new BusinessException(UserInfoExceptionCode.ROLE_CODE_DUPLICATE);
    }

    // 默认值设置
    if (dto.getStatus() == null) {
      dto.setStatus("ENABLED");
    }
    if (dto.getBuiltIn() == null) {
      dto.setBuiltIn(false);
    }
    RoleVO vo = roleRepository.save(dto);
    log.info("Role created: code={}, id={}", vo.getRoleCode(), vo.getId());
    eventPublisher.publishRoleEntityChanged(vo, "CREATED");
    return vo.getId();
  }

  /**
   * {@inheritDoc}
   *
   * @throws BusinessException 当角色不存在时抛出
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public boolean update(RoleDTO dto) {
    RoleVO existing = roleRepository.findById(dto.getId())
        .orElseThrow(() -> new BusinessException(UserInfoExceptionCode.ROLE_NOT_FOUND));

    RoleVO vo = roleRepository.save(dto);

    if (vo != null) {
      // 角色变更后失效其权限缓存
      evictRolePermissionCache(dto.getId());
      invalidatePermissionCache(vo.getRoleCode());
      eventPublisher.publishRoleEntityChanged(vo, "UPDATED");
    }

    return vo != null;
  }

  /**
   * {@inheritDoc}
   *
   * <p>删除前检查：内置角色不可删除、有用户关联的角色不可删除。 删除时同时清除角色-权限关联记录。
   *
   * <p>删除成功后主动失效角色权限缓存。
   *
   * @throws BusinessException 当角色不存在、为内置角色、或仍有用户关联时抛出
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public boolean removeById(String id) {
    RoleVO existing = roleRepository.findById(id)
        .orElseThrow(() -> new BusinessException(UserInfoExceptionCode.ROLE_NOT_FOUND));
    if (Boolean.TRUE.equals(existing.getBuiltIn())) {
      throw new BusinessException(UserInfoExceptionCode.ROLE_BUILTIN_CANNOT_DELETE);
    }

    // 检查用户关联
    if (userRoleRepository.countByRoleId(id) > 0) {
      throw new BusinessException(UserInfoExceptionCode.ROLE_HAS_USERS);
    }

    // 清除角色-权限关联记录
    rolePermissionRepository.deleteByRoleId(id);

    boolean result = roleRepository.deleteById(id);

    if (result) {
      // 角色删除后失效缓存
      evictRolePermissionCache(id);
      invalidatePermissionCache(existing.getRoleCode());
      eventPublisher.publishRoleEntityChanged(existing, "DELETED");
    }

    return result;
  }

  /**
   * {@inheritDoc}
   *
   * <p>先删除旧的角色-权限关联，再批量插入新关联（全量覆盖模式）。
   *
   * <p>分配成功后主动失效角色权限缓存。
   *
   * @throws BusinessException 当角色不存在时抛出
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public boolean assignPermissions(String roleId, List<String> permissionIds) {
    RoleVO roleVO = roleRepository.findById(roleId)
        .orElseThrow(() -> new BusinessException(UserInfoExceptionCode.ROLE_NOT_FOUND));

    // 清除旧的权限关联
    rolePermissionRepository.deleteByRoleId(roleId);

    // 批量插入新关联
    if (permissionIds != null && !permissionIds.isEmpty()) {
      List<RolePermissionDTO> dtoList = new ArrayList<>(permissionIds.size());
      for (String permId : permissionIds) {
        RolePermissionDTO rp = new RolePermissionDTO();
        rp.setRoleId(roleId);
        rp.setPermissionId(permId);
        dtoList.add(rp);
      }
      rolePermissionRepository.batchInsert(dtoList);
    }
    log.info("Permissions assigned to role {}: count={}", roleId,
        permissionIds != null ? permissionIds.size() : 0);

    // 权限分配后失效缓存
    evictRolePermissionCache(roleId);
    invalidatePermissionCache(roleVO.getRoleCode());

    return true;
  }

  /**
   * 角色或权限分配变更后，失效角色权限缓存并广播权限变更事件。
   *
   * <p>同时失效：getRolePermissionIds 的 roleId 维度缓存（由调用方 {@code evictRolePermissionCache} 负责）、
   * 鉴权链路的 roleCode 维度 DB 结果缓存（{@link DbRolePermissionLoader}），并通知 common-auth 监听器（Gateway 等）。
   *
   * @param roleCode 角色编码，可为 null（空值时仅广播菜单级事件）
   */
  private void invalidatePermissionCache(String roleCode) {
    try {
      permissionLoader.invalidate(roleCode);
      permissionChangeNotifier.notifyRoleChanged(roleCode);
    } catch (Exception e) {
      log.warn("Failed to invalidate permission cache: roleCode={}, error={}", roleCode,
          e.getMessage());
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>优先从 Redis 缓存读取角色权限列表，缓存未命中时查询数据库并写入缓存。
   *
   * @param roleId 角色 ID
   * @return 权限 ID 列表
   */
  @Override
  public List<String> getRolePermissionIds(String roleId) {
    String cacheKey = CACHE_KEY_ROLE_PERMISSIONS_PREFIX + roleId;

    // 1. 尝试从缓存获取
    try {
      String cachedJson = redisStringOps.get(cacheKey, String.class);
      if (cachedJson != null && !cachedJson.isBlank()) {
        List<String> cached = YdszJson.fromJson(cachedJson, List.class, String.class);
        if (cached != null) {
          log.debug("Role permissions loaded from cache: roleId={}", roleId);
          return cached;
        }
      }
    } catch (Exception e) {
      log.warn("Failed to load role permissions from cache, fallback to DB: {}", e.getMessage());
    }

    // 2. 缓存未命中，查询数据库
    List<String> permissionIds = rolePermissionRepository.findPermissionIdsByRoleId(roleId);

    // 3. 写入缓存（异步异常不影响业务）
    try {
      String json = YdszJson.toJson(permissionIds);
      redisStringOps.set(cacheKey, json, Duration.ofSeconds(CACHE_TTL_ROLE_PERMISSIONS));
    } catch (Exception e) {
      log.warn("Failed to cache role permissions: {}", e.getMessage());
    }

    return permissionIds;
  }

  /**
   * 失效角色权限缓存
   *
   * <p>角色删除/更新/权限分配时调用，确保缓存数据与数据库一致。
   *
   * @param roleId 角色 ID
   */
  private void evictRolePermissionCache(String roleId) {
    if (roleId == null) {
      return;
    }
    try {
      redisStringOps.del(CACHE_KEY_ROLE_PERMISSIONS_PREFIX + roleId);
      log.debug("Role permission cache evicted: roleId={}", roleId);
    } catch (Exception e) {
      log.warn("Failed to evict role permission cache: {}", e.getMessage());
    }
  }

  /**
   * 批量查询角色 ID → 角色名映射。
   *
   * <p>实现：单条 SQL 完成，已自动过滤逻辑删除数据。
   */
  @Override
  public Map<String, String> batchNamesByIds(Collection<String> roleIds) {
    if (roleIds == null || roleIds.isEmpty()) {
      return Collections.emptyMap();
    }
    List<String> distinctIds =
        roleIds.stream()
            .filter(id -> id != null && !id.isBlank())
            .distinct()
            .collect(Collectors.toList());
    if (distinctIds.isEmpty()) {
      return Collections.emptyMap();
    }
    List<RoleVO> roles = roleRepository.listByIds(distinctIds);
    Map<String, String> result = new LinkedHashMap<>(roles.size());
    for (RoleVO role : roles) {
      if (role.getRoleName() != null && !role.getRoleName().isBlank()) {
        result.put(role.getId(), role.getRoleName());
      }
    }
    return result;
  }
}
