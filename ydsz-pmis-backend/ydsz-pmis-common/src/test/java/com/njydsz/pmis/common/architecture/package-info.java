/**
 * ArchUnit 架构约束测试（P2-2 架构优化）。
 *
 * <p>使用 ArchUnit 在 CI 中强制校验模块依赖方向和分层架构规则，
 * 防止架构劣化和重复建设。
 *
 * <h3>覆盖规则</h3>
 * <ul>
 *   <li>common 模块不能依赖业务模块</li>
 *   <li>Controller 不能直接调用 Mapper</li>
 *   <li>Feign Client 必须集中在 common.feign 包</li>
 *   <li>禁止跨模块直连 Mapper（必须通过 Feign）</li>
 *   <li>模块间不能存在循环依赖</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
package com.njydsz.pmis.common.architecture;
