package com.njydsz.userinfo.server.auth.DbRolePermissionLoader;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.common.auth.model.RolePermissions;
import com.njydsz.common.auth.service.RolePermissionLoader;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.userinfo.domain.repository.MenuRepository;
import com.njydsz.userinfo.domain.repository.RolePermissionRepository;
import com.njydsz.userinfo.domain.repository.RoleRepository;
import com.njydsz.userinfo.domain.vo.MenuVO;
import com.njydsz.userinfo.domain.vo.RoleVO;
import com.njydsz.userinfo.server.config.UserInfoProperties;

/**
 * 基于数据库的角色权限加载器。
 *
 * <p>从 ydsz_rbac_role_permission 关联表按 roleId 查询权限 ID， 再从 ydsz_rbac_menu（权限表）加载菜单/按钮/API 权限集合。 实现 common-auth
 * 的 {@link RolePermissionLoader} SPI。
 *
 * <p><b>缓存策略（P0-1 修复）：</b>
 *
 * <ul>
 *   <li>DB 查询结果写入 Redis（key {@code userinfo:permission:Role:{roleCode}}，TTL 由
 *       {@code ydsz.userinfo.permission-cache-ttl-seconds} 外部化配置，默认 10 分钟）
 *   <li>菜单/角色/权限分配变更时调用 {@link #invalidate(String)} / {@link #invalidateAll()}
 *       主动失效，保证权限变更即时生效，不再依赖 TTL 自然过期
 *   <li>缓存读写异常均降级为 DB 直查，不影响鉴权主链路
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DbRolePermissionLoader implements RolePermissionLoader {

  /** 权限分类数（Menu / BUTTON / API 三类）。 */
  private static final int PERMISSION_CATEGORY_COUNT = 3;

  /** 角色权限缓存 Redis Key 前缀。 */
  private static final String CACHE_KEY_PREFIX = "userinfo:permission:Role:";

  private final MenuRepository menuRepository;
  private final RoleRepository roleRepository;
  private final RolePermissionRepository rolePermissionRepository;
  private final RedisStringOps redisStringOps;
  private final UserInfoProperties properties;

  @Override
  public RolePermissions loadByRoleCode(String roleCode) {
    if (roleCode == null || roleCode.isBlank()) {
      return RolePermissions.empty();
    }
    String cacheKey = buildCacheKey(roleCode);

    // 1. 尝试从缓存读取（异常降级为 DB 直查）
    try {
      String cached = redisStringOps.get(cacheKey, String.class);
      if (cached != null && !cached.isBlank()) {
        RolePermissions permissions = deserialize(cached);
        if (permissions != null) {
          log.debug("Role permissions loaded from cache: roleCode={}", roleCode);
          return permissions;
        }
      }
    } catch (Exception e) {
      log.warn("Failed to read role permissions cache, fallback to DB: roleCode={}, error={}",
          roleCode, e.getMessage());
    }

    // 2. 缓存未命中，查询数据库
    RolePermissions permissions = loadFromDb(roleCode);

    // 3. 回填缓存（空结果也缓存，防止穿透；异常不影响业务）
    try {
      redisStringOps.set(cacheKey, serialize(permissions),
          Duration.ofSeconds(properties.getPermissionCacheTtlSeconds()));
    } catch (Exception e) {
      log.warn("Failed to cache role permissions: roleCode={}, error={}", roleCode, e.getMessage());
    }

    return permissions;
  }

  /**
   * 从数据库加载角色权限（原始查询逻辑）。
   *
   * @param roleCode 角色编码
   * @return 角色权限集合；角色不存在或无权限时返回空集合
   */
  private RolePermissions loadFromDb(String roleCode) {
    // 1. 按 roleCode 查询角色
    RoleVO roleVO = roleRepository.findByRoleCode(roleCode).orElse(null);
    if (roleVO == null) {
      log.debug("Role not found for roleCode: {}", roleCode);
      return RolePermissions.empty();
    }

    // 2. 按 roleId 查询 role_permission 关联表
    List<String> permissionIds = rolePermissionRepository.findPermissionIdsByRoleId(roleVO.getId());

    if (permissionIds.isEmpty()) {
      return RolePermissions.empty();
    }

    // 3. 查询权限/菜单详情
    List<MenuVO> menus = menuRepository.findByIds(permissionIds).stream()
        .filter(m -> "ENABLED".equals(m.getStatus()))
        .collect(Collectors.toList());

    // 4. 按类型分类权限码
    Map<String, Set<String>> categorized = categorizePermissions(menus);

    return new RolePermissions(
        Collections.unmodifiableSet(categorized.get("Menu")),
        Collections.unmodifiableSet(categorized.get("BUTTON")),
        Collections.unmodifiableSet(categorized.get("API")));
  }

  /**
   * 失效指定角色的权限缓存（角色/权限分配变更时调用）。
   *
   * @param roleCode 角色编码，为空时忽略
   */
  public void invalidate(String roleCode) {
    if (roleCode == null || roleCode.isBlank()) {
      return;
    }
    try {
      redisStringOps.del(buildCacheKey(roleCode));
      log.debug("Role permissions cache evicted: roleCode={}", roleCode);
    } catch (Exception e) {
      log.warn("Failed to evict role permissions cache: roleCode={}, error={}",
          roleCode, e.getMessage());
    }
  }

  /**
   * 失效全部角色的权限缓存（菜单变更时调用，菜单影响所有角色）。
   */
  public void invalidateAll() {
    try {
      redisStringOps.delByPattern(CACHE_KEY_PREFIX + "*");
      log.info("All role permissions cache evicted");
    } catch (Exception e) {
      log.warn("Failed to evict all role permissions cache: {}", e.getMessage());
    }
  }

  private String buildCacheKey(String roleCode) {
    return CACHE_KEY_PREFIX + roleCode;
  }

  /**
   * 序列化权限集合为 JSON（{@code {"Menu":[],"button":[],"api":[]}}）。
   *
   * @param permissions 权限集合
   * @return JSON 字符串
   */
  private String serialize(RolePermissions permissions) {
    Map<String, Object> map = new HashMap<>(PERMISSION_CATEGORY_COUNT);
    map.put("Menu", permissions.getMenuPermissions());
    map.put("button", permissions.getButtonPermissions());
    map.put("api", permissions.getApiPermissions());
    return YdszJson.toJson(map);
  }

  /**
   * 反序列化 JSON 为权限集合。
   *
   * @param json JSON 字符串
   * @return 权限集合；解析失败返回 null
   */
  private RolePermissions deserialize(String json) {
    try {
      Map<String, Object> map = YdszJson.parseMap(json);
      if (map == null) {
        return null;
      }
      Set<String> menuPerms = toStringSet(map.get("Menu"));
      Set<String> buttonPerms = toStringSet(map.get("button"));
      Set<String> apiPerms = toStringSet(map.get("api"));
      return new RolePermissions(
          Collections.unmodifiableSet(menuPerms),
          Collections.unmodifiableSet(buttonPerms),
          Collections.unmodifiableSet(apiPerms));
    } catch (Exception e) {
      log.warn("Failed to parse role permissions cache json: {}", e.getMessage());
      return null;
    }
  }

  private Set<String> toStringSet(Object value) {
    if (value instanceof List<?> list) {
      Set<String> result = new HashSet<>();
      for (Object item : list) {
        if (item != null) {
          result.add(item.toString());
        }
      }
      return result;
    }
    return new HashSet<>(0);
  }

  /**
   * 按菜单类型分类权限码。
   *
   * @param menus 菜单列表
   * @return 分类后的权限码映射（Menu/BUTTON/API）
   */
  private Map<String, Set<String>> categorizePermissions(List<MenuVO> menus) {
    Set<String> menuPerms = new HashSet<>(16);
    Set<String> buttonPerms = new HashSet<>(16);
    Set<String> apiPerms = new HashSet<>(16);

    for (MenuVO menu : menus) {
      String permCode = menu.getPermissionCode();
      if (permCode == null || permCode.isBlank()) {
        continue;
      }
      String type = menu.getMenuType();
      if ("BUTTON".equals(type)) {
        buttonPerms.add(permCode);
      } else if ("API".equals(type)) {
        apiPerms.add(permCode);
      } else {
        menuPerms.add(permCode);
      }
    }
    Map<String, Set<String>> result = new HashMap<>(PERMISSION_CATEGORY_COUNT);
    result.put("Menu", menuPerms);
    result.put("BUTTON", buttonPerms);
    result.put("API", apiPerms);
    return result;
  }
}
