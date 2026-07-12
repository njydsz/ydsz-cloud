package com.njydsz.pmis.common;

/**
 * PMIS Common All 聚合 Starter 标记类。
 *
 * <p>本类无实际逻辑，仅用于：
 * <ol>
 *   <li>确保 all 模块 JAR 非空（Maven 约定）</li>
 *   <li>作为聚合 Starter 的存在标记，业务模块通过引入
 *       {@code ydsz-pmis-common-all} 依赖即可获得全部公共能力</li>
 * </ol>
 *
 * <p>所有自动配置由各子模块的 {@code AutoConfiguration.imports} 文件注册，
 * 不需要此模块额外声明。
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
public final class CommonAllMarker {

    private CommonAllMarker() {
    }
}
