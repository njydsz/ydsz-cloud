package com.njydsz.common.base.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.core.domain.properties.HasName;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.njydsz.common.base.architecture.ArchitecturePredicates.inBusinessModules;

/**
 * 线程池架构守护测试。
 *
 * <p>验证 YDIZ-CONC-001 规范：业务模块禁止直接依赖 {@link java.util.concurrent.ThreadPoolExecutor}
 * 或 {@link java.util.concurrent.ScheduledThreadPoolExecutor}（包括 new 构造、类型引用、方法返回值），
 * 同时禁止依赖 {@link java.util.concurrent.Executors} 工厂类（newFixedThreadPool/newSingleThreadScheduledExecutor
 * 等快捷方法本质等价于直接实例化），必须通过 ydsz-common-thread 模块统一管理。
 *
 * <p>豁免范围：ydsz-common-thread 模块（线程池工厂本身）、ydsz-common-cache（缓存调度）、
 * ydsz-common-socket（WebSocket 内部推送调度）、ydsz-common-util（TempFileManager 异步任务）、
 * ydsz-common-search（UnifiedSearchService 异步索引构建）、ydsz-common-docs（文档解析异步处理）。
 *
 * <p>违规时测试失败提示，需修改为通过 {@code ThreadPoolExecutorFactory.getBean("poolName")} 获取命名线程池。
 *
 * @since 26.09.23
 */
@DisplayName("线程池架构守护 — YDIZ-CONC-001")
class ThreadPoolArchitectureTest {

  /** 扫描范围：com.njydsz 全平台包 */
  private static final String ROOT_PACKAGE = "com.njydsz";

  @Test
  @DisplayName("YDIZ-CONC-001: 业务模块禁止直接依赖 ThreadPoolExecutor")
  void businessModulesMustNotDependOnThreadPoolExecutor() {
    JavaClass threadPoolExecutor = importClass(java.util.concurrent.ThreadPoolExecutor.class);
    JavaClass scheduledThreadPoolExecutor =
        importClass(java.util.concurrent.ScheduledThreadPoolExecutor.class);
    JavaClass threadPoolTaskExecutor =
        importClass(
            org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor.class);

    ArchRule rule =
        noClasses()
            .that(inBusinessModules())
            .should()
            .dependOnClassesThat(
                HasName.Predicates.name(threadPoolExecutor.getName())
                    .or(HasName.Predicates.name(scheduledThreadPoolExecutor.getName())))

            .as(
                "业务模块禁止直接使用 ThreadPoolExecutor / ScheduledThreadPoolExecutor，"
                    + "必须通过 ydsz-common-thread 的 ThreadPoolExecutorFactory 获取命名线程池");

    ArchRule taskExecutorRule =
        noClasses()
            .that(inBusinessModules())
            .should()
            .dependOnClassesThat(HasName.Predicates.name(threadPoolTaskExecutor.getName()))
            .as(
                "业务模块禁止直接使用 ThreadPoolTaskExecutor，"
                    + "必须通过 ydsz-common-thread 的声明式配置创建");

    var importedClasses =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT_PACKAGE);

    rule.check(importedClasses);
    taskExecutorRule.check(importedClasses);
  }

  @Test
  @DisplayName("YDIZ-CONC-001: 业务模块禁止依赖 Executors 工厂类")
  void businessModulesMustNotDependOnExecutors() {
    JavaClass executors = importClass(java.util.concurrent.Executors.class);

    ArchRule rule =
        noClasses()
            .that(inBusinessModules())
            .should()
            .dependOnClassesThat(HasName.Predicates.name(executors.getName()))
            .as(
                "业务模块禁止使用 Executors.newFixedThreadPool / newSingleThreadScheduledExecutor 等"
                    + "快捷工厂方法（等价于直接实例化线程池），"
                    + "必须通过 ydsz-common-thread 的 ExecutorUtils / ThreadPoolExecutorFactory 获取线程池");

    var importedClasses =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT_PACKAGE);

    rule.check(importedClasses);
  }

  private JavaClass importClass(Class<?> clazz) {
    return new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importClass(clazz);
  }
}
