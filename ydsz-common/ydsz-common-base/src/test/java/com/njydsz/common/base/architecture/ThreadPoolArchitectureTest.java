package com.njydsz.common.base.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;

/**
 * 线程池架构守护测试.
 *
 * <p>验证 YDIZ-CONC-001 规范：业务模块禁止直接 {@code new ThreadPoolExecutor(} 或
 * {@code new ScheduledThreadPoolExecutor(}，必须通过 ydsz-common-thread 统一管理。
 *
 * <p>豁免范围：ydsz-common-thread 模块（线程池工厂本身）、ydsz-common-cache（缓存调度）、
 * ydsz-common-socket（WebSocket 内部推送调度）、ydsz-common-util（TempFileManager 临时文件清理）、
 * ydsz-common-search（UnifiedSearchService 搜索索引构建）、ydsz-common-docs（文档解析异步）、
 * ydsz-common-tenant（异步任务租户上下文装饰示例）。
 *
 * <p>业务模块违规时测试失败，提示修改为 {@code ThreadPoolExecutorFactory.getBean("poolName")}。
 *
 * @since 26.09.23
 */
@DisplayName("线程池架构守护 — YDIZ-CONC-001")
class ThreadPoolArchitectureTest {

  /** 扫描范围：所有业务模块 + common 模块 */
  private static final String ALL_MODULES = "com.njydsz..";

  /** 豁免包：common 内部模块允许直接使用 ThreadPoolExecutor */
  private static final String[] EXEMPT_PACKAGES = {
    "com.njydsz.common.thread..",
    "com.njydsz.common.cache..",
    "com.njydsz.common.socket..",
    "com.njydsz.common.util..",
    "com.njydsz.common.search..",
    "com.njydsz.common.docs..",
    "com.njydsz.common.tenant.."
  };

  @Test
  @DisplayName("YDIZ-CONC-001: 业务模块禁止 new ThreadPoolExecutor")
  void businessModulesMustNotCreateThreadPoolExecutor() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("com.njydsz.{system,userinfo,message,workflow,cronjob,"
                + "nextwiki,literule,agent,generator,gateway}..")
            .should()
            .callConstructorWhere(
                target ->
                    target.getOwner().isEquivalentTo(java.util.concurrent.ThreadPoolExecutor.class)
                        || target.getOwner().isEquivalentTo(
                            java.util.concurrent.ScheduledThreadPoolExecutor.class))
            .as("业务模块禁止 new ThreadPoolExecutor/ScheduledThreadPoolExecutor，"
                + "必须通过 ydsz-common-thread 的 ThreadPoolExecutorFactory 获取线程池");

    rule.check(
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ALL_MODULES));
  }

  @Test
  @DisplayName("YDIZ-CONC-001-SUPP: 业务模块禁止 new ThreadPoolTaskExecutor")
  void businessModulesMustNotCreateThreadPoolTaskExecutor() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("com.njydsz.{system,userinfo,message,workflow,cronjob,"
                + "nextwiki,literule,agent,generator,gateway}..")
            .should()
            .callConstructorWhere(
                target ->
                    target.getOwner().isAssignableTo(
                        org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor.class))
            .as("业务模块禁止 new ThreadPoolTaskExecutor，"
                + "必须通过 ydsz-common-thread 的声明式配置创建");

    rule.check(
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ALL_MODULES));
  }
}
