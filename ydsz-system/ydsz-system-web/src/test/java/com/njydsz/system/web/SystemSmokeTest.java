package com.njydsz.system.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * 系统引擎冒烟测试 — 验证 Spring ApplicationContext 加载（CI 基础门禁）
 *
 * <p>确保模块编译通过、Bean 装配正确、循环依赖不存在。集成测试时连接真实/容器化 PG + Redis，
 * dev profile 使用 Mock 环境。
 *
 * @author ydsz-team
 * @since 26.10.05
 */
@SpringBootTest(classes = SystemApplication.class)
@ActiveProfiles("dev")
@DisplayName("系统引擎 - Context 加载冒烟测试")
class SystemSmokeTest {

  @Test
  @DisplayName("Spring ApplicationContext 应正常启动无异常")
  void contextLoads() {
    // @SpringBootTest 启动即验证；若存在循环依赖或 Bean 装配失败，此方法会抛出异常
    assertDoesNotThrow(() -> {
      // 故意留空：context 加载成功即告通过
    }, "系统引擎 Spring ApplicationContext 应正常加载，若失败请检查循环依赖 / Bean 装配");
  }
}
