/**
 * 特性开关（Feature Flag）层。
 *
 * <p>动态控制功能开启 / 关闭、灰度比例、白名单等。配置来源：Nacos 远程配置 + 本地内存缓存，
 * 业务侧通过 {@link com.njydsz.pmis.common.featureflag.FeatureFlagService} 实时查询。
 *
 * <h3>典型使用场景</h3>
 * <ul>
 *   <li>新功能灰度发布：仅对指定部门 / 用户开放</li>
 *   <li>风险功能开关：如大模型调用、Agent 自动决策等高风险能力</li>
 *   <li>A/B 实验：按比例分流</li>
 *   <li>紧急回滚：故障时一键关闭</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>开关 key 使用小写 + 点分命名（如 {@code feature.ai-agent.enabled}）</li>
 *   <li>默认 {@code false}，新功能需显式开启</li>
 *   <li>对性能敏感场景使用本地缓存（{@code FeatureFlagSnapshot}），避免每次调用都查 Nacos</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.common.featureflag;
