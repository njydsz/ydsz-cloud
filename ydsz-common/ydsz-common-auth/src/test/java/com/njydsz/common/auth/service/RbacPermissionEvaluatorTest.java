package com.njydsz.common.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.auth.annotation.PermissionMode;
import com.njydsz.common.auth.config.AuthProperties;
import com.njydsz.common.auth.exception.PermissionDeniedException;
import com.njydsz.common.auth.exception.PermissionDeniedException.PermissionType;
import com.njydsz.common.auth.model.RolePermissions;
import com.njydsz.common.core.context.RequestCacheHolder;
import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.exception.custom.BusinessException;

/**
 * {@link RbacPermissionEvaluator} 判定语义单元测试 —— 接口鉴权的 fail-closed 契约。
 *
 * <p><b>为什么需要这组测试：</b>{@code @AuthApiPermission} 是全站接口权限的唯一执行入口，
 * 而 ydsz-common-auth 模块此前<b>测试数为 0</b>：允许/拒绝的全部判定分支没有任何自动化证据。
 * 本测试把"什么情况下必须拒绝"钉死，重点覆盖三类一旦回归就会静默变成 fail-open 的路径：
 * 角色解析、权限集合加载失败、Redis 故障降级。
 *
 * <p><b>夹具约定：</b>{@code setUp} 预置 {@code RequestContext.tenantId} 与请求级用户信息缓存，
 * 使 {@code resolveTenantId()} 走 ThreadLocal 短路——否则每个用例都要为 token 加载打桩，
 * 一旦桩缺失 Mockito 严格桩会判红，掩盖真正要测的判定分支。
 *
 * @author ydsz-team
 * @since 26.10.27
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RBAC 权限校验器 — 允许/拒绝判定语义")
class RbacPermissionEvaluatorTest {

  private static final String TENANT_ID = "1";

  /** 注解夹具宿主：借真实注解实例避免手写 AuthApiPermission 实现体。 */
  static class Fixtures {
    @AuthApiPermission(apiCodes = {"sys:user:add", "sys:user:delete"})
    static void apiAnd() {}

    @AuthApiPermission(mode = PermissionMode.OR, apiCodes = {"sys:user:add", "sys:user:delete"})
    static void apiOr() {}

    @AuthApiPermission(roleCodes = {"ops"})
    static void roleOnly() {}

    @AuthApiPermission(mode = PermissionMode.OR, roleCodes = {"ops", "admin"})
    static void roleOr() {}

    /** 只写了注解、既不给 apiCodes 也不给 roleCodes —— 校验会被整体跳过（风险口径见对应用例）。 */
    @AuthApiPermission
    static void emptyRequirement() {}
  }

  @Mock private RbacUserInfoService userInfoService;
  @Mock private RolePermissionLoader rolePermissionLoader;
  @Mock private RolePermissionCacheService rolePermissionCacheService;

  private AuthProperties properties;
  private RbacPermissionEvaluator evaluator;

  @BeforeEach
  void setUp() {
    RequestContext.clear();
    RequestContext.setTenantId(TENANT_ID);
    RequestCacheHolder.setCachedUserInfoMap(new HashMap<>(Map.of("userId", "42", "roleCode", "ops")));

    properties = new AuthProperties();
    evaluator =
        new RbacPermissionEvaluator(
            properties, userInfoService, rolePermissionLoader, rolePermissionCacheService);
  }

  @AfterEach
  void tearDown() {
    RequestContext.clear();
  }

  private static AuthApiPermission annotationOf(String fixtureMethod) {
    try {
      Method m = Fixtures.class.getDeclaredMethod(fixtureMethod);
      return m.getAnnotation(AuthApiPermission.class);
    } catch (NoSuchMethodException e) {
      throw new IllegalStateException("注解夹具缺失: " + fixtureMethod, e);
    }
  }

  private static Map<String, Object> userWithRoles(String roleCodesCsv) {
    Map<String, Object> userInfo = new HashMap<>(4);
    userInfo.put("userId", "42");
    userInfo.put("roleCode", roleCodesCsv);
    return userInfo;
  }

  /** 让权限集合直接命中本地缓存桩，把用例焦点留在判定逻辑而非加载链路。 */
  private void grantedApiPermissions(RolePermissions permissions) {
    when(rolePermissionCacheService.getCachedPermissions(anyString())).thenReturn(permissions);
  }

  // ==================== 1. 开关与"空要求" ====================

  @Nested
  @DisplayName("开关语义")
  class Switches {

    @Test
    @DisplayName("ydsz.auth.enabled=false 时无条件放行（全局 kill switch，权限集合完全不参与）")
    void disabled_shouldAllowEverything() {
      properties.setEnabled(false);

      // 权限集合为空 + 要求具体权限码，仍然放行：这就是"关开关 = 全站接口不设防"
      evaluator.validateApi(userWithRoles("guest"), annotationOf("apiAnd"));

      verifyNoInteractions(rolePermissionCacheService, rolePermissionLoader);
    }

    @Test
    @DisplayName("注解未填 apiCodes 与 roleCodes → 直接放行（漏填不报错，等于不保护）")
    void emptyRequirement_shouldAllow() {
      evaluator.validateApi(userWithRoles("guest"), annotationOf("emptyRequirement"));

      verifyNoInteractions(rolePermissionCacheService, rolePermissionLoader);
    }

    @Test
    @DisplayName("注解为 null → 直接放行（切面侧已有非空判断，此处兜底）")
    void nullAnnotation_shouldAllow() {
      evaluator.validateApi(userWithRoles("ops"), null);

      verifyNoInteractions(rolePermissionCacheService, rolePermissionLoader);
    }
  }

  // ==================== 2. 角色维度 ====================

  @Nested
  @DisplayName("角色校验")
  class RoleChecks {

    @Test
    @DisplayName("AND 模式缺少任一必需角色 → 403 / C01063，且权限码集合完全不加载")
    void roleAnd_missingRole_shouldDeny() {
      assertThatThrownBy(
              () -> evaluator.validateApi(userWithRoles("guest"), annotationOf("roleOnly")))
          .isInstanceOfSatisfying(
              PermissionDeniedException.class,
              e -> {
                assertThat(e.getHttpStatus()).isEqualTo(403);
                assertThat(e.getCode()).isEqualTo("C01063");
                assertThat(e.getPermissionType()).isEqualTo(PermissionType.API);
                assertThat(e.getRequiredPermissions()).containsExactly("ops");
              });

      verifyNoInteractions(rolePermissionCacheService, rolePermissionLoader);
    }

    @Test
    @DisplayName("AND 模式角色满足 → 放行，不触碰权限集合")
    void roleAnd_matchingRole_shouldAllow() {
      evaluator.validateApi(userWithRoles("ops,audit"), annotationOf("roleOnly"));

      verifyNoInteractions(rolePermissionCacheService, rolePermissionLoader);
    }

    @Test
    @DisplayName("OR 模式：命中一个角色即放行；全不命中才拒绝")
    void roleOr_semantics() {
      evaluator.validateApi(userWithRoles("admin"), annotationOf("roleOr"));

      assertThatThrownBy(
              () -> evaluator.validateApi(userWithRoles("guest"), annotationOf("roleOr")))
          .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    @DisplayName("roleCode 为 CSV 且带空白 → 拆分后仍能命中（不能被整串比较误拒）")
    void roleCsvWithSpaces_shouldTrimAndMatch() {
      evaluator.validateApi(userWithRoles("guest, ops ,audit"), annotationOf("roleOnly"));
    }

    @Test
    @DisplayName("用户信息缺少 roleCode 字段 → 视为无角色并拒绝，不得当成超管放行")
    void missingRoleField_shouldDeny() {
      Map<String, Object> userInfo = new HashMap<>(2);
      userInfo.put("userId", "42");

      assertThatThrownBy(
              () -> evaluator.validateApi(userInfo, annotationOf("roleOnly")))
          .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    @DisplayName("ignoreRoles 命中的角色跳过全部权限码校验（超管旁路只在显式配置后生效）")
    void superAdmin_bypassesApiCodes() {
      properties.setIgnoreRoles("super_admin");

      evaluator.validateApi(userWithRoles("super_admin"), annotationOf("apiAnd"));

      // 旁路必须发生在加载权限集合之前，否则超管每次请求都要打 Redis
      verifyNoInteractions(rolePermissionCacheService, rolePermissionLoader);
    }

    @Test
    @DisplayName("ignoreRoles 默认空串 → admin 不是超管，仍按权限码判定并拒绝")
    void defaultIgnoreRoles_adminIsNotSuperAdmin() {
      assertThat(properties.getIgnoreRoles())
          .as("默认值：未显式配置超管角色白名单")
          .isEmpty();
      grantedApiPermissions(new RolePermissions(Set.of(), Set.of(), Set.of("sys:user:add")));

      assertThatThrownBy(
              () -> evaluator.validateApi(userWithRoles("admin"), annotationOf("apiAnd")))
          .isInstanceOf(PermissionDeniedException.class);
    }
  }

  // ==================== 3. 权限码维度 ====================

  @Nested
  @DisplayName("权限码校验")
  class PermissionCodeChecks {

    @Test
    @DisplayName("AND 模式：权限码齐全才放行")
    void apiAnd_allGranted_shouldAllow() {
      grantedApiPermissions(
          new RolePermissions(
              Set.of(), Set.of(), Set.of("sys:user:add", "sys:user:delete")));

      evaluator.validateApi(userWithRoles("ops"), annotationOf("apiAnd"));
    }

    @Test
    @DisplayName("AND 模式：缺少任一权限码 → 拒绝，异常上下文带上缺失项与已有项")
    void apiAnd_partiallyGranted_shouldDeny() {
      grantedApiPermissions(new RolePermissions(Set.of(), Set.of(), Set.of("sys:user:add")));

      assertThatThrownBy(
              () -> evaluator.validateApi(userWithRoles("ops"), annotationOf("apiAnd")))
          .isInstanceOfSatisfying(
              PermissionDeniedException.class,
              e -> {
                assertThat(e.getRequiredPermissions()).contains("sys:user:delete");
                assertThat(e.getGrantedPermissions()).containsExactly("sys:user:add");
                assertThat(e.getCheckMode()).isEqualTo("AND");
              });
    }

    @Test
    @DisplayName("OR 模式：命中任一权限码即放行")
    void apiOr_anyGranted_shouldAllow() {
      grantedApiPermissions(new RolePermissions(Set.of(), Set.of(), Set.of("sys:user:delete")));

      evaluator.validateApi(userWithRoles("ops"), annotationOf("apiOr"));
    }

    @Test
    @DisplayName("通配符开启：sys:user:* 覆盖 sys:user:add/delete")
    void wildcardEnabled_prefixPatternCoversConcreteCodes() {
      assertThat(properties.isWildcardEnabled()).isTrue();
      grantedApiPermissions(new RolePermissions(Set.of(), Set.of(), Set.of("sys:user:*")));

      evaluator.validateApi(userWithRoles("ops"), annotationOf("apiAnd"));
    }

    @Test
    @DisplayName("通配符关闭：同一份授权不再覆盖具体权限码 → 拒绝")
    void wildcardDisabled_sameGrantShouldDeny() {
      properties.setWildcardEnabled(false);
      grantedApiPermissions(new RolePermissions(Set.of(), Set.of(), Set.of("sys:user:*")));

      assertThatThrownBy(
              () -> evaluator.validateApi(userWithRoles("ops"), annotationOf("apiAnd")))
          .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    @DisplayName("用户一个角色都没有 → 空权限集短路，既不查缓存也不调加载器，直接拒绝")
    void emptyRoles_shouldNotTouchCache() {
      assertThatThrownBy(
              () -> evaluator.validateApi(userWithRoles(""), annotationOf("apiAnd")))
          .isInstanceOf(PermissionDeniedException.class);

      verifyNoInteractions(rolePermissionCacheService, rolePermissionLoader);
    }
  }

  // ==================== 4. 加载链路降级 ====================

  @Nested
  @DisplayName("权限集合加载")
  class PermissionLoading {

    @Test
    @DisplayName("Redis 故障 + 批量加载抛异常 → 拒绝，且不把空权限写回缓存（防缓存毒化）")
    void redisUnavailable_loadFails_shouldDenyWithoutCaching() {
      evaluator.setRedisAvailable(false);
      when(rolePermissionCacheService.getCachedPermissions(anyString())).thenReturn(null);
      when(rolePermissionLoader.loadByRoleCodes(anySet()))
          .thenThrow(new IllegalStateException("Redis connection refused"));

      assertThatThrownBy(
              () -> evaluator.validateApi(userWithRoles("ops"), annotationOf("apiAnd")))
          .isInstanceOf(PermissionDeniedException.class);

      verify(rolePermissionCacheService, never())
          .cachePermissions(anyString(), anySet(), any(RolePermissions.class));
    }

    @Test
    @DisplayName("加载器返回 null 条目 → 跳过该角色继续合并其余权限，不得 NPE")
    void loaderWithNullEntry_shouldSkipAndContinue() {
      when(rolePermissionCacheService.getCachedPermissions(anyString())).thenReturn(null);
      Map<String, RolePermissions> loaded = new HashMap<>(4);
      loaded.put("ops", null);
      loaded.put(
          "audit", new RolePermissions(Set.of(), Set.of(), Set.of("sys:user:add", "sys:user:delete")));
      when(rolePermissionLoader.loadByRoleCodes(anySet())).thenReturn(loaded);

      evaluator.validateApi(userWithRoles("ops,audit"), annotationOf("apiAnd"));

      verify(rolePermissionCacheService)
          .cachePermissions(anyString(), anySet(), any(RolePermissions.class));
    }
  }

  // ==================== 5. 用户信息加载 fail-closed ====================

  @Nested
  @DisplayName("用户信息加载")
  class UserInfoLoading {

    @Test
    @DisplayName("token 为空 → TOKEN_MISSING（401），不允许匿名通过")
    void blankToken_shouldThrowTokenMissing() {
      assertThatThrownBy(() -> evaluator.loadUserInfo("   "))
          .isInstanceOf(BusinessException.class)
          .hasFieldOrPropertyWithValue("httpStatus", 401);

      verifyNoInteractions(userInfoService);
    }

    @Test
    @DisplayName("用户信息服务返回空 Map → TOKEN_EXPIRED（401）")
    void emptyUserInfo_shouldThrowTokenExpired() {
      // 清掉 setUp 预置的请求级缓存，否则 loadCurrentUserInfo() 直接命中缓存不会触达加载链路
      RequestCacheHolder.clear();
      when(userInfoService.loadCurrentToken()).thenReturn("tok");
      when(userInfoService.loadUserInfoMap("tok")).thenReturn(Collections.emptyMap());

      assertThatThrownBy(() -> evaluator.loadCurrentUserInfo())
          .isInstanceOf(BusinessException.class)
          .hasFieldOrPropertyWithValue("httpStatus", 401);
    }

    @Test
    @DisplayName("同一请求内两次校验只加载一次用户信息（请求级缓存承诺成立）")
    void requestLevelCache_loadsOnce() {
      when(userInfoService.loadCurrentToken()).thenReturn("tok");
      when(userInfoService.loadUserInfoMap("tok"))
          .thenReturn(new HashMap<>(Map.of("userId", "42", "roleCode", "ops")));
      RequestCacheHolder.clear();

      // 只给 roleCodes 要求：避免用例被权限集合加载链路牵走，焦点是"用户信息只加载一次"
      evaluator.validateApi(new String[] {}, new String[] {"ops"}, PermissionMode.AND);
      evaluator.validateApi(new String[] {}, new String[] {"ops"}, PermissionMode.AND);

      verify(userInfoService, times(1)).loadCurrentToken();
      verify(userInfoService, times(1)).loadUserInfoMap("tok");
    }

    @Test
    @DisplayName("hasPermission：底层加载异常时返回 false 而非抛出（fail-closed，且不外泄异常）")
    void hasPermission_onLoadFailure_returnsFalse() {
      RequestCacheHolder.clear();
      when(userInfoService.loadCurrentToken()).thenThrow(new IllegalStateException("Redis down"));

      assertThat(evaluator.hasPermission("sys:user:delete")).isFalse();
    }
  }
}
