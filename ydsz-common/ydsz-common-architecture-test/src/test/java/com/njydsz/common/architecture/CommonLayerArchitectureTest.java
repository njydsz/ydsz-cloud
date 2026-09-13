package com.njydsz.common.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * ydsz-cloud-common 六层分级体系自动化架构守护。
 *
 * <p>层级规则：
 *
 * <ul>
 *   <li>L1 (json/util/cache/excel)：禁止依赖其他 common 子模块（L2+）和 Spring
 *   <li>L2 (core)：仅依赖 L1
 *   <li>L3 (domain/exception)：仅依赖 L1-L2
 *   <li>L4 (jdbc/redis/lock/thread/tenant)：仅依赖 L1-L3，同级 L4 互引不违规
 *   <li>L5 (auth/safe/feign/...)：仅依赖 L1-L4，同级 L5 互引不违规
 *   <li>L6 (base/app/web)：允许依赖全部
 * </ul>
 *
 * <p>同级依赖不视为违规（如 auth→safe 均为 L5）；仅当低层→高层反向依赖时触发失败。
 *
 * @author ydzs-team
 * @since 26.09.13
 */
@AnalyzeClasses(
    packages = "com.njydsz.common",
    importOptions = ImportOption.DoNotIncludeTests.class)
public class CommonLayerArchitectureTest {

  private static final String L1_PKG = "com.njydsz.common.json..";
  private static final String L1_UTIL = "com.njydsz.common.util..";
  private static final String L1_CACHE = "com.njydsz.common.cache..";
  private static final String L1_EXCEL = "com.njydsz.common.excel..";
  private static final String L2_CORE = "com.njydsz.common.core..";
  private static final String L3_DOMAIN = "com.njydsz.common.domain..";
  private static final String L3_EXCEPTION = "com.njydsz.common.exception..";
  private static final String L4_JDBC = "com.njydsz.common.jdbc..";
  private static final String L4_REDIS = "com.njydsz.common.redis..";
  private static final String L4_LOCK = "com.njydsz.common.lock..";
  private static final String L4_THREAD = "com.njydsz.common.thread..";
  private static final String L4_TENANT = "com.njydsz.common.tenant..";
  private static final String L5_AUTH = "com.njydsz.common.auth..";
  private static final String L5_SAFE = "com.njydsz.common.safe..";
  private static final String L5_FEIGN = "com.njydsz.common.feign..";
  private static final String L5_AUDIT = "com.njydsz.common.audit..";
  private static final String L5_NOTIFY = "com.njydsz.common.notify..";
  private static final String L5_QUEUE = "com.njydsz.common.queue..";
  private static final String L5_EVENT = "com.njydsz.common.event..";
  private static final String L5_CONFIG = "com.njydsz.common.config..";
  private static final String L5_SOCKET = "com.njydsz.common.socket..";
  private static final String L5_NETTY = "com.njydsz.common.netty..";
  private static final String L5_FILE = "com.njydsz.common.file..";
  private static final String L5_DOCS = "com.njydsz.common.docs..";
  private static final String L5_SEARCH = "com.njydsz.common.search..";
  private static final String L5_SENTRY = "com.njydsz.common.sentry..";
  private static final String L5_SEATA = "com.njydsz.common.seata..";
  private static final String L6_BASE = "com.njydsz.common.base..";
  private static final String L6_WEB = "com.njydsz.common.web..";
  private static final String L6_APP = "com.njydsz.common.app..";

  /** 所有 common 子模块包路径汇总（不含 L6，L6 作为最外层允许引用全部）。 */
  private static final String[] ALL_LAYER_PACKAGES = {
    L1_PKG, L1_UTIL, L1_CACHE, L1_EXCEL,
    L2_CORE,
    L3_DOMAIN, L3_EXCEPTION,
    L4_JDBC, L4_REDIS, L4_LOCK, L4_THREAD, L4_TENANT,
    L5_AUTH, L5_SAFE, L5_FEIGN, L5_AUDIT, L5_NOTIFY,
    L5_QUEUE, L5_EVENT, L5_CONFIG, L5_SOCKET, L5_NETTY,
    L5_FILE, L5_DOCS, L5_SEARCH, L5_SENTRY, L5_SEATA
  };

  /** 非 L1 的 common 子模块（L1 以外的所有模块）。 */
  private static final String[] NON_L1_LAYER_PACKAGES = {
    L2_CORE,
    L3_DOMAIN, L3_EXCEPTION,
    L4_JDBC, L4_REDIS, L4_LOCK, L4_THREAD, L4_TENANT,
    L5_AUTH, L5_SAFE, L5_FEIGN, L5_AUDIT, L5_NOTIFY,
    L5_QUEUE, L5_EVENT, L5_CONFIG, L5_SOCKET, L5_NETTY,
    L5_FILE, L5_DOCS, L5_SEARCH, L5_SENTRY, L5_SEATA,
    L6_BASE, L6_WEB, L6_APP
  };

  /** 非 L1/L2 的 common 子模块。 */
  private static final String[] NON_L1_L2_PACKAGES = {
    L3_DOMAIN, L3_EXCEPTION,
    L4_JDBC, L4_REDIS, L4_LOCK, L4_THREAD, L4_TENANT,
    L5_AUTH, L5_SAFE, L5_FEIGN, L5_AUDIT, L5_NOTIFY,
    L5_QUEUE, L5_EVENT, L5_CONFIG, L5_SOCKET, L5_NETTY,
    L5_FILE, L5_DOCS, L5_SEARCH, L5_SENTRY, L5_SEATA,
    L6_BASE, L6_WEB, L6_APP
  };

  /** 非 L1/L2/L3 的 common 子模块。 */
  private static final String[] NON_L1_L2_L3_PACKAGES = {
    L4_JDBC, L4_REDIS, L4_LOCK, L4_THREAD, L4_TENANT,
    L5_AUTH, L5_SAFE, L5_FEIGN, L5_AUDIT, L5_NOTIFY,
    L5_QUEUE, L5_EVENT, L5_CONFIG, L5_SOCKET, L5_NETTY,
    L5_FILE, L5_DOCS, L5_SEARCH, L5_SENTRY, L5_SEATA,
    L6_BASE, L6_WEB, L6_APP
  };

  /** 非 L1-L4 的 common 子模块（L5+L6）。 */
  private static final String[] NON_L1_TO_L4_PACKAGES = {
    L5_AUTH, L5_SAFE, L5_FEIGN, L5_AUDIT, L5_NOTIFY,
    L5_QUEUE, L5_EVENT, L5_CONFIG, L5_SOCKET, L5_NETTY,
    L5_FILE, L5_DOCS, L5_SEARCH, L5_SENTRY, L5_SEATA,
    L6_BASE, L6_WEB, L6_APP
  }

  // ========== L1 纯度规则 ==========

  /**
   * L1 层禁止依赖任何非 L1 的 common 子模块。
   *
   * <p>json/util/cache/excel 之间允许互相同层依赖，除此之外不得引用 L2+ 模块。
   */
  @ArchTest
  static final ArchRule L1_SHOULD_NOT_DEPEND_ON_HIGHER_LAYERS =
      noClasses()
          .that()
          .resideInAnyPackage(L1_PKG, L1_CACHE, L1_EXCEL)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(NON_L1_LAYER_PACKAGES)
          .because("L1 层禁止依赖 L2+ 的 common 子模块（YDIZ-ARCH-001 L1 纯度）");

  /**
   * util 除同层 L1（json/cache/excel）外不得依赖 L2+ 模块。
   */
  @ArchTest
  static final ArchRule L1_UTIL_SHOULD_ONLY_DEPEND_L1 =
      noClasses()
          .that()
          .resideInPackage(L1_UTIL)
          .and()
          .resideOutsideOfPackages(
              "com.njydsz.common.util.internal..",
              "com.njydsz.common.util.config..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(NON_L1_LAYER_PACKAGES)
          .orShould()
          .dependOnClassesThat()
          .resideInPackage(L2_CORE)
          .because("L1 util 层仅允许依赖同层 L1 的 json/cache/excel（YDIZ-ARCH-001）");

  // ========== L2 纯度规则 ==========

  /**
   * L2 core 仅可依赖 L1 层。
   */
  @ArchTest
  static final ArchRule L2_CORE_SHOULD_ONLY_DEPEND_L1 =
      noClasses()
          .that()
          .resideInPackage(L2_CORE)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(NON_L1_L2_PACKAGES)
          .because("L2 core 仅允许依赖 L1 层（YDIZ-ARCH-001）");

  // ========== L3 纯度规则 ==========

  /**
   * L3 domain/exception 仅可依赖 L1-L2 层。
   */
  @ArchTest
  static final ArchRule L3_SHOULD_ONLY_DEPEND_L1_L2 =
      noClasses()
          .that()
          .resideInAnyPackage(L3_DOMAIN, L3_EXCEPTION)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(NON_L1_L2_L3_PACKAGES)
          .because("L3 domain/exception 仅允许依赖 L1-L2 层（YDIZ-ARCH-001）");

  // ========== L4 纯度规则 ==========

  /**
   * L4 模块仅可依赖 L1-L3 层（允许同层 L4 互引）。
   */
  @ArchTest
  static final ArchRule L4_SHOULD_ONLY_DEPEND_L1_L3 =
      noClasses()
          .that()
          .resideInAnyPackage(L4_JDBC, L4_REDIS, L4_LOCK, L4_THREAD, L4_TENANT)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(NON_L1_L2_L3_PACKAGES)
          .andShould()
          .dependOnClassesThat()
          .resideInAnyPackage(
              L5_AUTH, L5_SAFE, L5_FEIGN, L5_AUDIT, L5_NOTIFY,
              L5_QUEUE, L5_EVENT, L5_CONFIG, L5_SOCKET, L5_NETTY,
              L5_FILE, L5_DOCS, L5_SEARCH, L5_SENTRY, L5_SEATA)
          .because("L4 模块仅允许依赖 L1-L3 层及同层 L4（YDIZ-ARCH-001）");

  // ========== L5 纯度规则 ==========

  /**
   * L5 模块仅可依赖 L1-L4 层（允许同层 L5 互引）。
   */
  @ArchTest
  static final ArchRule L5_SHOULD_NOT_DEPEND_ON_L6 =
      noClasses()
          .that()
          .resideInAnyPackage(
              L5_AUTH, L5_SAFE, L5_FEIGN, L5_AUDIT, L5_NOTIFY,
              L5_QUEUE, L5_EVENT, L5_CONFIG, L5_SOCKET, L5_NETTY,
              L5_FILE, L5_DOCS, L5_SEARCH, L5_SENTRY, L5_SEATA)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(L6_BASE, L6_WEB, L6_APP)
          .because("L5 模块禁止依赖 L6 层（YDIZ-ARCH-001 单向依赖）");

  // ========== 包循环依赖检测 ==========

  /**
   * common 子模块包之间禁止循环依赖。
   */
  @ArchTest
  static final ArchRule COMMON_MODULES_SHOULD_HAVE_NO_CYCLES =
      slices()
          .matching("com.njydsz.common.(*)..")
          .should()
          .beFreeOfCycles()
          .because("common 子模块之间禁止循环依赖");
}
