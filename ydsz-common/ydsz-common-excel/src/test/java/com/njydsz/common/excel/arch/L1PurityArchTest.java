package com.njydsz.common.excel.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * L1 工具纯度守护测试 — ydsz-common-excel（云顶编码规范 §22.2「L1 工具纯度」）。
 *
 * <p><b>规范原文</b>：L1 模块必须零内部依赖（仅依赖 JDK + 经批准的第三方库）。
 *
 * <p><b>历史缺陷</b>：本模块曾依赖 {@code ydsz-common-thread}（L4，ExecutorUtils 虚拟线程）
 * 与 {@code ydsz-common-exception}（L3，BusinessException/CoreExceptionCode），
 * 构成 L1→L4 与 L1→L3 反向依赖。26.11.06 已改用 JDK 21 {@code Thread.ofVirtual()}
 * 与本模块自带的 {@code ExcelOperationException}。
 *
 * <p>本测试将该约束固化为可执行规则，任何新增 L2+ 内部依赖都会判红。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@AnalyzeClasses(
    packages = "com.njydsz.common.excel",
    importOptions = {ImportOption.DoNotIncludeTests.class})
final class L1PurityArchTest {

  /** L2+ 内部模块前缀（出现任一即为反向依赖）。 */
  private static final String[] L2_PLUS_PACKAGES = {
    "com.njydsz.common.core..",
    "com.njydsz.common.domain..",
    "com.njydsz.common.exception..",
    "com.njydsz.common.jdbc..",
    "com.njydsz.common.redis..",
    "com.njydsz.common.lock..",
    "com.njydsz.common.thread..",
    "com.njydsz.common.tenant..",
    "com.njydsz.common.auth..",
    "com.njydsz.common.safe..",
    "com.njydsz.common.feign..",
    "com.njydsz.common.audit..",
    "com.njydsz.common.notify..",
    "com.njydsz.common.queue..",
    "com.njydsz.common.event..",
    "com.njydsz.common.netty..",
    "com.njydsz.common.socket..",
    "com.njydsz.common.file..",
    "com.njydsz.common.search..",
    "com.njydsz.common.config..",
    "com.njydsz.common.docs..",
    "com.njydsz.common.base..",
    "com.njydsz.common.web..",
  };

  /**
   * L1 模块禁止依赖任何 L2+ 内部模块。
   */
  @ArchTest
  static final ArchRule l1_must_not_depend_on_l2_plus =
      noClasses()
          .that()
          .resideInAPackage("com.njydsz.common.excel..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(L2_PLUS_PACKAGES)
          .because(
              "云顶编码规范 §22.2「L1 工具纯度」：L1 模块必须零内部依赖，"
                  + "仅允许 JDK + 经批准的第三方库。如需通用能力，请下沉到 L1 或在本模块内自持实现。");
}
