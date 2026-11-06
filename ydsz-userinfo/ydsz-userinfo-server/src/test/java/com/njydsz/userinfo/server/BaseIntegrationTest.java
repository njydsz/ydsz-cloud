package com.njydsz.userinfo.server;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.njydsz.common.test.TestcontainersBase;

/**
 * Userinfo 模块集成测试基类。
 *
 * <p>继承 {@link TestcontainersBase}，复用平台统一的 PostgreSQL 18 + Redis 7 Testcontainers
 * 容器配置（JDBC URL / Redis 连接信息通过 {@code @DynamicPropertySource} 自动注册）。
 *
 * <p><b>使用前提：</b>运行环境需安装 Docker（Testcontainers 通过 Docker 拉取镜像并启动容器）。
 * 无 Docker 环境下测试以"可见跳过"结束，不会变成红灯。
 *
 * <p><b>数据隔离：</b>每个测试类运行完毕后应在 {@code @AfterEach} 中清理业务数据，
 * 或使用 {@code @Transactional} 让 Spring 自动回滚事务。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@SpringBootTest(classes = TestUserInfoApplication.class)
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
public abstract class BaseIntegrationTest extends TestcontainersBase {
  // 容器与连接属性由 TestcontainersBase 统一提供，此处无需重复定义
}
