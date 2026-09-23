package com.njydsz.common.base.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;

/**
 * 通用架构谓词工厂。
 *
 * <p>定义模块归属判断谓词，供 ArchUnit 测试复用。
 *
 * @since 26.09.23
 */
public final class ArchitecturePredicates {

  /** 八大引擎模块 + 网关的包前缀（业务模块范围） */
  private static final String[] BUSINESS_MODULE_PREFIXES = {
    "com.njydsz.system..",
    "com.njydsz.userinfo..",
    "com.njydsz.message..",
    "com.njydsz.workflow..",
    "com.njydsz.cronjob..",
    "com.njydsz.nextwiki..",
    "com.njydsz.literule..",
    "com.njydsz.agent..",
    "com.njydsz.generator..",
    "com.njydsz.gateway..",
  };

  /** ydzs-common 底座层的包前缀（业务模块内判断时需排除） */
  private static final String[] COMMON_BASE_PACKAGE = {
    // 注意：common/* 模块不处于上述业务包树下，故无需在此显式排除
  };

  private ArchitecturePredicates() {
    // 静态工厂，禁止实例化
  }

  /**
   * 判断是否为业务引擎模块（八大引擎 + 网关）。
   *
   * <p>八大引擎模块 + 网关，此类模块的代码受业务模块级约束。
   * ydzs-common-* 包树（前缀为 com.njydsz.common.）已天然不在业务模块包树下，
   * 直接使用包前缀匹配即可区分。
   *
   * @return 业务模块谓词
   */
  public static DescribedPredicate<JavaClass> inBusinessModules() {
    return JavaClass.Predicates.resideInAnyPackage(BUSINESS_MODULE_PREFIXES)
        .as("reside in business module (ydsz-{engine} or ydsz-gateway)");
  }
}
