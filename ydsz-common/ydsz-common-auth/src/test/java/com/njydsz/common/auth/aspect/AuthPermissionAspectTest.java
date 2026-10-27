package com.njydsz.common.auth.aspect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.auth.exception.PermissionDeniedException;
import com.njydsz.common.auth.exception.PermissionDeniedException.PermissionType;
import com.njydsz.common.auth.metrics.AuthMetricsCollector;
import com.njydsz.common.auth.service.RbacPermissionEvaluator;

/**
 * {@link AuthPermissionAspect} 单元测试 —— {@code @AuthApiPermission} 的执行时序守护。
 *
 * <p><b>为什么需要这组测试：</b>全仓 163 个 Controller Smoke Test 全部使用
 * {@code MockMvcBuilders.standaloneSetup(controller)}，装配的是<b>未被 Spring 代理的原始对象</b>，
 * 因此本切面在冒烟测试里根本不会运行——"接口鉴权是否真的拦在业务方法之前"这一安全命题，
 * 在新增本测试之前<b>没有任何自动化证据</b>（ydsz-common-auth 模块此前测试数为 0）。
 * 用 MockMvc 写 RBAC 反例只会证明"切面没被装配"，属于恒绿假象，故改为直接对切面下单测。
 *
 * <p>锁定 5 条不可回退的语义：
 *
 * <ol>
 *   <li>校验失败时目标方法<b>绝不执行</b>（拦截先于业务，而非事后返回错误体）
 *   <li>类级 + 方法级注解同时存在时两次校验都执行（AND 语义，缺一不可）
 *   <li>未标注解的方法零评估器交互（切面不得越权干预无关方法）
 *   <li>注解解析缓存的键必须包含宿主类：同一继承方法的<b>不同子类</b>不得复用彼此的类级注解
 *   <li>耗时指标在放行与拒绝两条路径都上报（{@code finally} 语义，拒绝不得丢指标）
 * </ol>
 *
 * @author ydsz-team
 * @since 26.10.27
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("接口权限切面 — @AuthApiPermission 执行时序")
class AuthPermissionAspectTest {

  /** 目标方法被真实执行时返回的哨兵值，用于区分"切面放行"与"切面代跑"。 */
  private static final String RAN = "TARGET_METHOD_RAN";

  @Mock private RbacPermissionEvaluator evaluator;
  @Mock private AuthMetricsCollector metricsCollector;
  @Mock private ProceedingJoinPoint joinPoint;
  @Mock private MethodSignature signature;

  private AuthPermissionAspect aspect;

  @BeforeEach
  void setUp() {
    aspect = new AuthPermissionAspect(evaluator);
  }

  // ==================== 测试夹具：真实注解，避免手写注解实现体 ====================

  /** 仅类级注解；方法本身不标注，用于校验"类级注解覆盖其全部方法"。 */
  @AuthApiPermission(apiCodes = "sys:class:read")
  static class ClassLevelOnly {
    String guarded() {
      return RAN;
    }
  }

  /** 类级 + 方法级注解同时存在，用于校验 AND 语义。 */
  @AuthApiPermission(apiCodes = "sys:class:read")
  static class ClassAndMethod {
    @AuthApiPermission(apiCodes = "sys:method:write")
    String guarded() {
      return RAN;
    }
  }

  /** 无任何注解的宿主类。 */
  static class NotAnnotated {
    String plain() {
      return RAN;
    }

    @AuthApiPermission(apiCodes = "sys:method:only")
    String guarded() {
      return RAN;
    }
  }

  /** 注解声明在子类、方法继承自基类，用于暴露"缓存键漏掉宿主类"的串号风险。 */
  static class SharedBase {
    String inherited() {
      return RAN;
    }
  }

  @AuthApiPermission(apiCodes = "sys:tenant:read")
  static class SubTenant extends SharedBase {}

  @AuthApiPermission(apiCodes = "sys:audit:read")
  static class SubAudit extends SharedBase {}

  // ==================== 桩装配 ====================

  /**
   * 把 joinPoint 绑定到指定方法与宿主实例。
   *
   * <p>{@code proceed()} 在此不打桩：拒绝路径不应触达它，触达即由 {@code verify(never())} 判红。
   */
  private void bind(Method method, Object target) throws Throwable {
    when(joinPoint.getSignature()).thenReturn(signature);
    when(signature.getMethod()).thenReturn(method);
    when(joinPoint.getTarget()).thenReturn(target);
  }

  private void bindRan(Method method, Object target) throws Throwable {
    bind(method, target);
    when(joinPoint.proceed()).thenReturn(RAN);
  }

  private static Method methodOf(Class<?> owner, String name) {
    try {
      return owner.getDeclaredMethod(name);
    } catch (NoSuchMethodException e) {
      throw new IllegalStateException("测试夹具方法缺失: " + owner + "#" + name, e);
    }
  }

  private static PermissionDeniedException apiDenied() {
    return PermissionDeniedException.denied().permissionType(PermissionType.API).build();
  }

  // ==================== 1. 拒绝先于业务 ====================

  @Nested
  @DisplayName("拒绝时序")
  class DenialOrdering {

    @Test
    @DisplayName("权限校验失败 → 目标方法一次都不执行，异常原样抛出")
    void denied_shouldNeverInvokeTargetMethod() throws Throwable {
      Method guarded = methodOf(ClassLevelOnly.class, "guarded");
      bind(guarded, new ClassLevelOnly());
      when(evaluator.loadCurrentUserInfo()).thenReturn(Map.of("userId", "42"));
      doThrow(apiDenied())
          .when(evaluator)
          .validateApi(any(Map.class), any(AuthApiPermission.class));

      assertThatThrownBy(() -> aspect.doApiAround(joinPoint))
          .isInstanceOf(PermissionDeniedException.class)
          .hasFieldOrPropertyWithValue("httpStatus", 403)
          .hasFieldOrPropertyWithValue("code", "C01063");

      // 核心安全断言：注解未通过时业务方法未被调用（不是"调用了再返回 403"）
      verify(joinPoint, never()).proceed();
    }
  }

  // ==================== 2. 类级/方法级 AND 语义 ====================

  @Nested
  @DisplayName("注解层级")
  class AnnotationLevels {

    @Test
    @DisplayName("仅方法级注解 → 校验该注解一次并放行")
    void methodLevel_shouldValidateOnce() throws Throwable {
      Method guarded = methodOf(NotAnnotated.class, "guarded");
      bindRan(guarded, new NotAnnotated());
      when(evaluator.loadCurrentUserInfo()).thenReturn(Map.of("userId", "42"));

      Object result = aspect.doApiAround(joinPoint);

      assertThat(result).isEqualTo(RAN);
      ArgumentCaptor<AuthApiPermission> captor = ArgumentCaptor.forClass(AuthApiPermission.class);
      verify(evaluator, times(1)).validateApi(any(Map.class), captor.capture());
      assertThat(captor.getValue().apiCodes()).containsExactly("sys:method:only");
    }

    @Test
    @DisplayName("仅类级注解（方法未标注）→ 校验类级注解一次并放行")
    void classLevel_shouldValidateClassAnnotation() throws Throwable {
      Method guarded = methodOf(ClassLevelOnly.class, "guarded");
      bindRan(guarded, new ClassLevelOnly());
      when(evaluator.loadCurrentUserInfo()).thenReturn(Map.of("userId", "42"));

      assertThat(aspect.doApiAround(joinPoint)).isEqualTo(RAN);

      ArgumentCaptor<AuthApiPermission> captor = ArgumentCaptor.forClass(AuthApiPermission.class);
      verify(evaluator).validateApi(any(Map.class), captor.capture());
      assertThat(captor.getValue().apiCodes()).containsExactly("sys:class:read");
    }

    @Test
    @DisplayName("类级 + 方法级并存 → 两次校验都执行（AND，缺一不可）")
    void classAndMethod_shouldValidateBothAnnotations() throws Throwable {
      Method guarded = methodOf(ClassAndMethod.class, "guarded");
      bindRan(guarded, new ClassAndMethod());
      when(evaluator.loadCurrentUserInfo()).thenReturn(Map.of("userId", "42"));

      aspect.doApiAround(joinPoint);

      ArgumentCaptor<AuthApiPermission> captor = ArgumentCaptor.forClass(AuthApiPermission.class);
      verify(evaluator, times(2)).validateApi(any(Map.class), captor.capture());
      List<String> checkedCodes =
          captor.getAllValues().stream()
              .flatMap(ann -> Arrays.stream(ann.apiCodes()))
              .toList();
      // 顺序为「先类级后方法级」，任一侧被漏掉都会缩窄权限要求
      assertThat(checkedCodes).containsExactly("sys:class:read", "sys:method:write");
    }

    @Test
    @DisplayName("类与方法均无注解 → 零评估器交互并直接放行")
    void noAnnotation_shouldNotTouchEvaluator() throws Throwable {
      Method plain = methodOf(NotAnnotated.class, "plain");
      bindRan(plain, new NotAnnotated());

      assertThat(aspect.doApiAround(joinPoint)).isEqualTo(RAN);

      verifyNoInteractions(evaluator);
    }
  }

  // ==================== 3. 注解解析缓存的键正确性 ====================

  @Nested
  @DisplayName("注解解析缓存")
  class AnnotationCache {

    @Test
    @DisplayName("同一继承方法、不同宿主子类 → 各自解析自己的类级注解，不得串号")
    void inheritedMethodWithDifferentSubclasses_shouldNotShareCachedAnnotation() throws Throwable {
      // Method 对象来自基类：两次调用命中同一个 Method 键，若缓存只按 Method 取键，
      // 第二次会复用第一次宿主子类的类级注解 → 对 SubAudit 的实例校验 sys:tenant:read（串号）。
      Method inherited = methodOf(SharedBase.class, "inherited");
      aspect.setMetricsCollector(null);

      when(joinPoint.getSignature()).thenReturn(signature);
      when(signature.getMethod()).thenReturn(inherited);
      when(joinPoint.proceed()).thenReturn(RAN);
      when(evaluator.loadCurrentUserInfo()).thenReturn(Map.of("userId", "42"));

      when(joinPoint.getTarget()).thenReturn(new SubTenant(), new SubAudit());
      aspect.doApiAround(joinPoint);
      aspect.doApiAround(joinPoint);

      ArgumentCaptor<AuthApiPermission> captor = ArgumentCaptor.forClass(AuthApiPermission.class);
      verify(evaluator, times(2)).validateApi(any(Map.class), captor.capture());
      List<String> checkedCodes =
          captor.getAllValues().stream()
              .flatMap(ann -> Arrays.stream(ann.apiCodes()))
              .toList();
      assertThat(checkedCodes)
          .as("两次调用必须分别命中各自宿主类的权限码")
          .containsExactly("sys:tenant:read", "sys:audit:read");
    }
  }

  // ==================== 4. 指标上报 ====================

  @Nested
  @DisplayName("耗时指标")
  class Metrics {

    @Test
    @DisplayName("放行路径上报一次校验耗时")
    void allowed_shouldRecordCheckTime() throws Throwable {
      Method guarded = methodOf(ClassLevelOnly.class, "guarded");
      bindRan(guarded, new ClassLevelOnly());
      when(evaluator.loadCurrentUserInfo()).thenReturn(Map.of("userId", "42"));
      aspect.setMetricsCollector(metricsCollector);

      aspect.doApiAround(joinPoint);

      verify(metricsCollector).recordCheckTime(anyLong());
    }

    @Test
    @DisplayName("拒绝路径同样上报耗时（finally 语义，权限拒绝不得丢指标）")
    void denied_shouldStillRecordCheckTime() throws Throwable {
      Method guarded = methodOf(ClassLevelOnly.class, "guarded");
      bind(guarded, new ClassLevelOnly());
      when(evaluator.loadCurrentUserInfo()).thenReturn(Map.of("userId", "42"));
      doThrow(apiDenied())
          .when(evaluator)
          .validateApi(any(Map.class), any(AuthApiPermission.class));
      aspect.setMetricsCollector(metricsCollector);

      assertThatThrownBy(() -> aspect.doApiAround(joinPoint))
          .isInstanceOf(PermissionDeniedException.class);

      verify(metricsCollector).recordCheckTime(anyLong());
    }

    @Test
    @DisplayName("采集器未配置时静默跳过（向后兼容，不抛 NPE）")
    void withoutCollector_shouldNotFail() throws Throwable {
      Method guarded = methodOf(ClassLevelOnly.class, "guarded");
      bindRan(guarded, new ClassLevelOnly());
      when(evaluator.loadCurrentUserInfo()).thenReturn(Map.of("userId", "42"));

      assertThat(aspect.doApiAround(joinPoint)).isEqualTo(RAN);
    }
  }
}
