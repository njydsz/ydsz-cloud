package com.njydsz.common.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import java.util.Arrays;

/**
 * ydsz-cloud-common 六层分级体系自动化架构守护。
 *
 * <p>层级规则：
 *
 * <ul>
 *   <li>L1 (json/cache/excel)：禁止依赖任何其他 common 子模块（L2+）
 *   <li>L1 (util)：仅可依赖同层 L1；config 子包豁免（Spring @Configuration 动态配置能力）
 *   <li>L2 (core)：仅可依赖 L1
 *   <li>L3 (domain/exception)：仅可依赖 L1-L2
 *   <li>L4 (jdbc/redis/lock/thread/tenant)：仅可依赖 L1-L3，同层 L4 互引豁免
 *   <li>L5 (15 个服务模块)：仅可依赖 L1-L4，同层 L5 互引豁免，禁止依赖 L6
 *   <li>L6 (base/app/web)：允许依赖全部
 * </ul>
 *
 * <h2>豁免说明</h2>
 *
 * <ul>
 *   <li>同层互引（如 auth→safe 均为 L5）不违规，仅当低层→高层反向依赖时触发失败。
 *   <li>L1 util.config 子包豁免：该子包提供 Spring 动态配置能力（@ConfigurationProperties 绑定等），
 *       需要引入 spring-boot-autoconfigure，无法保持纯零依赖。
 * </ul>
 *
 * <h2>接入方式</h2>
 *
 * <pre>
 * cd ydsz-common
 * mvn test -pl ydsz-common-architecture-test -Dcheckstyle.skip=true
 * </pre>
 *
 * <p>CI 构建门禁：本测试失败应阻断合并（表明六层分级体系被破坏）。
 *
 * @author ydsz-team
 * @since 26.09.13
 */
@AnalyzeClasses(
    packages = "com.njydsz.common",
    importOptions = ImportOption.DoNotIncludeTests.class)
public class CommonLayerArchitectureTest {

  // ========== 包路径常量 ==========

  /** L1 工具层。 */
  private static final String JSON_PKG = "com.njydsz.common.json..";
  private static final String UTIL_PKG = "com.njydsz.common.util..";
  private static final String CACHE_PKG = "com.njydsz.common.cache..";
  private static final String EXCEL_PKG = "com.njydsz.common.excel..";
  private static final String UTIL_CONFIG_PKG = "com.njydsz.common.util.config..";

  /** L2 核心响应层。 */
  private static final String CORE_PKG = "com.njydsz.common.core..";

  /** L3 领域基类层。 */
  private static final String DOMAIN_PKG = "com.njydsz.common.domain..";
  private static final String EXCEPTION_PKG = "com.njydsz.common.exception..";

  /** L4 数据基础层。 */
  private static final String JDBC_PKG = "com.njydsz.common.jdbc..";
  private static final String REDIS_PKG = "com.njydsz.common.redis..";
  private static final String LOCK_PKG = "com.njydsz.common.lock..";
  private static final String THREAD_PKG = "com.njydsz.common.thread..";
  private static final String TENANT_PKG = "com.njydsz.common.tenant..";

  /** L5 业务服务层（15 个）。 */
  private static final String AUTH_PKG = "com.njydsz.common.auth..";
  private static final String SAFE_PKG = "com.njydsz.common.safe..";
  private static final String FEIGN_PKG = "com.njydsz.common.feign..";
  private static final String AUDIT_PKG = "com.njydsz.common.audit..";
  private static final String NOTIFY_PKG = "com.njydsz.common.notify..";
  private static final String QUEUE_PKG = "com.njydsz.common.queue..";
  private static final String EVENT_PKG = "com.njydsz.common.event..";
  private static final String CONFIG_PKG = "com.njydsz.common.config..";
  private static final String SOCKET_PKG = "com.njydsz.common.socket..";
  private static final String NETTY_PKG = "com.njydsz.common.netty..";
  private static final String FILE_PKG = "com.njydsz.common.file..";
  private static final String DOCS_PKG = "com.njydsz.common.docs..";
  private static final String SEARCH_PKG = "com.njydsz.common.search..";
  private static final String SENTRY_PKG = "com.njydsz.common.sentry..";
  private static final String SEATA_PKG = "com.njydsz.common.seata..";

  /** L6 应用层。 */
  private static final String BASE_PKG = "com.njydsz.common.base..";
  private static final String WEB_PKG = "com.njydsz.common.web..";
  private static final String APP_PKG = "com.njydsz.common.app..";

  /** 所有非 L1 的子模块（L2~L6）。 */
  private static final String[] NON_L1_PACKAGES = {
    CORE_PKG, DOMAIN_PKG, EXCEPTION_PKG,
    JDBC_PKG, REDIS_PKG, LOCK_PKG, THREAD_PKG, TENANT_PKG,
    AUTH_PKG, SAFE_PKG, FEIGN_PKG, AUDIT_PKG, NOTIFY_PKG,
    QUEUE_PKG, EVENT_PKG, CONFIG_PKG, SOCKET_PKG, NETTY_PKG,
    FILE_PKG, DOCS_PKG, SEARCH_PKG, SENTRY_PKG, SEATA_PKG,
    BASE_PKG, WEB_PKG, APP_PKG
  };

  /** L4 层所有模块。 */
  private static final String[] L4_PACKAGES = {
    JDBC_PKG, REDIS_PKG, LOCK_PKG, THREAD_PKG, TENANT_PKG
  };

  // ========== L1 纯度规则 ==========

  /**
   * L1 层（json/cache/excel）禁止依赖任何非 L1 的 common 子模块。
   *
   * <h3>违规示例</h3>
   *
   * <pre>
   * com.njydsz.common.cache.config.CacheProperties 引用 com.njydsz.common.core.response.ApiResult
   * → 违规：L1 依赖 L2
   * </pre>
   */
  @ArchTest
  static final ArchRule L1_SHOULD_NOT_DEPEND_ON_HIGHER_LAYERS =
      noClasses()
          .that()
          .resideInAnyPackage(JSON_PKG, CACHE_PKG, EXCEL_PKG)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(NON_L1_PACKAGES)
          .because(
              "L1 层禁止依赖 L2+ 的 common 子模块（YDIZ-ARCH-001 L1 纯度规则）");

  /**
   * L1 util 层仅可依赖同层 L1（json/cache/excel），禁止依赖 L2+。
   *
   * <p>豁免条件：util.config 子包允许引用 Spring 注解（@ConfigurationProperties 等），
   * 该子包实际上承担了"配置绑定"职责而非纯工具函数。
   */
  @ArchTest
  static final ArchRule L1_UTIL_SHOULD_ONLY_DEPEND_L1 =
      noClasses()
          .that()
          .resideInAPackage(UTIL_PKG)
          .and()
          .resideOutsideOfPackages(UTIL_CONFIG_PKG)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(NON_L1_PACKAGES)
          .as(
              "L1 util 层仅允许依赖同层 L1 模块（YDIZ-ARCH-001 单向依赖）");

  // ========== L2 纯度规则 ==========

  /**
   * L2 core 仅可依赖 L1 层。
   */
  @ArchTest
  static final ArchRule L2_CORE_SHOULD_ONLY_DEPEND_L1 =
      noClasses()
          .that()
          .resideInAPackage(CORE_PKG)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              Arrays.asList(NON_L1_PACKAGES).toArray(new String[0]))
          .because("L2 core 仅允许依赖 L1 层（YDIZ-ARCH-001）");

  // ========== L3 纯度规则 ==========

  /**
   * L3 domain/exception 仅可依赖 L1-L2 层。
   */
  @ArchTest
  static final ArchRule L3_SHOULD_ONLY_DEPEND_L1_L2 =
      noClasses()
          .that()
          .resideInAnyPackage(DOMAIN_PKG, EXCEPTION_PKG)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              JDBC_PKG, REDIS_PKG, LOCK_PKG, THREAD_PKG, TENANT_PKG,
              AUTH_PKG, SAFE_PKG, FEIGN_PKG, AUDIT_PKG, NOTIFY_PKG,
              QUEUE_PKG, EVENT_PKG, CONFIG_PKG, SOCKET_PKG, NETTY_PKG,
              FILE_PKG, DOCS_PKG, SEARCH_PKG, SENTRY_PKG, SEATA_PKG,
              BASE_PKG, WEB_PKG, APP_PKG)
          .because("L3 domain/exception 仅允许依赖 L1-L2 层（YDIZ-ARCH-001）");

  // ========== L4 纯度规则 ==========

  /**
   * L4 模块仅可依赖 L1-L3 层。同层 L4 互引不违规（如 tenant→jdbc 均属 L4）。
   */
  @ArchTest
  static final ArchRule L4_SHOULD_NOT_DEPEND_ON_L5_L6 =
      noClasses()
          .that()
          .resideInAnyPackage(JDBC_PKG, REDIS_PKG, LOCK_PKG, THREAD_PKG, TENANT_PKG)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              AUTH_PKG, SAFE_PKG, FEIGN_PKG, AUDIT_PKG, NOTIFY_PKG,
              QUEUE_PKG, EVENT_PKG, CONFIG_PKG, SOCKET_PKG, NETTY_PKG,
              FILE_PKG, DOCS_PKG, SEARCH_PKG, SENTRY_PKG, SEATA_PKG,
              BASE_PKG, WEB_PKG, APP_PKG)
          .because("L4 模块禁止依赖 L5/L6 层（YDIZ-ARCH-001 单向依赖）");

  // ========== L5 纯度规则 ==========

  /**
   * L5 模块禁止依赖 L6 层。同层 L5 互引不违规（auth→safe、feign→safe、notify→event 等均属同级）。
   */
  @ArchTest
  static final ArchRule L5_SHOULD_NOT_DEPEND_ON_L6 =
      noClasses()
          .that()
          .resideInAnyPackage(
              AUTH_PKG, SAFE_PKG, FEIGN_PKG, AUDIT_PKG, NOTIFY_PKG,
              QUEUE_PKG, EVENT_PKG, CONFIG_PKG, SOCKET_PKG, NETTY_PKG,
              FILE_PKG, DOCS_PKG, SEARCH_PKG, SENTRY_PKG, SEATA_PKG)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(BASE_PKG, WEB_PKG, APP_PKG)
          .because("L5 模块禁止依赖 L6 层（YDIZ-ARCH-001 单向依赖）");

  // ========== 包循环依赖检测 ==========

  /**
   * common 子模块包之间禁止循环依赖。
   *
   * <p>同级互引（如 auth→safe、feign→safe）如果形成闭环也应避免；此规则捕获跨层循环。
   */
  @ArchTest
  static final ArchRule COMMON_MODULES_SHOULD_HAVE_NO_CYCLES =
      slices()
          .matching("com.njydsz.common.(*)..")
          .should()
          .beFreeOfCycles()
          .because("common 子模块之间禁止循环依赖（YDIZ-ARCH-001 拓扑约束）");
}
