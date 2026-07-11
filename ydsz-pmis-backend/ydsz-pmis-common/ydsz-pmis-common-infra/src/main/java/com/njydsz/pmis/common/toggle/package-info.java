/**
 * 统一开关控制面包（P0-4 架构优化）。
 *
 * <p>将灰度路由（Canary）、特性开关（FeatureFlag）、AB 测试统一到一个控制面，
 * 替代各模块各自独立实现的 CanaryService / FeatureFlagService / ABTestService。
 *
 * <h3>核心类</h3>
 * <ul>
 *   <li>{@link com.njydsz.pmis.common.toggle.ToggleFacade} — 统一入口门面</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.6.0 (P0-4)
 */
package com.njydsz.pmis.common.toggle;
