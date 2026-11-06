package com.njydsz.cronjob.web.filter;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.njydsz.cronjob.domain.constants.CronjobConstants;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 内部派发路径契约一致性测试（P0-1 守护）。
 *
 * <p>背景：节点间派发是<b>服务直连</b>（{@code http://host:port} + 常量路径），不经过网关，
 * 因此 {@link CronjobConstants#INTERNAL_API_PREFIX} 必须与 InternalJobController 的类级
 * {@code @RequestMapping} 逐字一致，且不得包含 {@code /api} 前缀。
 *
 * <p>历史缺陷：常量曾误写为 {@code /api/cronjob/internal}，导致
 * <ul>
 *   <li>{@code RemoteTaskClient} 拼出的 URL 与 Controller 映射不匹配 → 派发 404</li>
 *   <li>{@code InternalTokenFilter} 前缀匹配恒不命中 → 令牌校验从未生效</li>
 * </ul>
 *
 * <p>本测试通过反射读取真实注解值做双向断言，任何一端变更未同步都会判红。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
class InternalApiPathConsistencyTest {

  /** 被守护的 Controller（节点间派发的唯一入口）。 */
  private static final String CONTROLLER_CLASS =
      "com.njydsz.cronjob.web.controller.job.InternalJobController";

  @Test
  @DisplayName("常量前缀不得包含 /api 段（服务直连不经网关）")
  void internalApiPrefixMustNotContainApiSegment() {
    assertThat(CronjobConstants.INTERNAL_API_PREFIX)
        .doesNotStartWith("/api")
        .doesNotContain("/api/");
  }

  @Test
  @DisplayName("常量前缀必须与 InternalJobController 类级 @RequestMapping 逐字一致")
  void constantMustMatchControllerRequestMapping() throws ClassNotFoundException {
    RequestMapping requestMapping =
        Class.forName(CONTROLLER_CLASS).getAnnotation(RequestMapping.class);

    assertThat(requestMapping).isNotNull();
    assertThat(requestMapping.value())
        .as("InternalJobController 类级 @RequestMapping 应恰好声明一个路径")
        .hasSize(1);
    assertThat(requestMapping.value()[0])
        .as("常量与 Controller 映射不一致会导致派发 404 + 鉴权失效")
        .isEqualTo(CronjobConstants.INTERNAL_API_PREFIX);
  }

  @Test
  @DisplayName("三个内部路径必须对应 Controller 中真实存在的映射方法")
  void internalPathsMustMatchRealControllerMethods() throws ClassNotFoundException {
    Class<?> controller = Class.forName(CONTROLLER_CLASS);

    Set<String> postPaths = Arrays.stream(controller.getDeclaredMethods())
        .filter(method -> method.isAnnotationPresent(PostMapping.class))
        .flatMap(method -> Arrays.stream(method.getAnnotation(PostMapping.class).value()))
        .collect(Collectors.toSet());

    assertThat(postPaths)
        .as("InternalJobController 应声明 execute / executeSubTask / executeBatch")
        .contains("/execute", "/executeSubTask", "/executeBatch");

    assertThat(CronjobConstants.INTERNAL_EXECUTE_PATH)
        .isEqualTo(CronjobConstants.INTERNAL_API_PREFIX + "/execute");
    assertThat(CronjobConstants.INTERNAL_SUB_TASK_PATH)
        .isEqualTo(CronjobConstants.INTERNAL_API_PREFIX + "/executeSubTask");
    assertThat(CronjobConstants.INTERNAL_BATCH_PATH)
        .isEqualTo(CronjobConstants.INTERNAL_API_PREFIX + "/executeBatch");
  }

  @Test
  @DisplayName("内部派发端点不得暴露 GET 映射")
  void internalEndpointsMustNotExposeReadMappings() throws ClassNotFoundException {
    Class<?> controller = Class.forName(CONTROLLER_CLASS);

    for (Method method : controller.getDeclaredMethods()) {
      GetMapping getMapping = method.getAnnotation(GetMapping.class);
      if (getMapping == null) {
        continue;
      }
      assertThat(getMapping.value())
          .as("内部派发 Controller 不应暴露 GET 端点: %s", method.getName())
          .isEmpty();
    }
  }
}
