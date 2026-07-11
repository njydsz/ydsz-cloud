/**
 * Feign 跨服务调用 DTO 子包。
 *
 * <p>与 {@code com.njydsz.pmis.common.feign} 配套使用，承载跨服务接口的入参 / 出参。
 * 本包下的 DTO 必须与被调方模块的内部 DTO 解耦，避免：
 * <ul>
 *   <li>被调方字段调整导致上游崩溃（兼容性失控）</li>
 *   <li>上游模型污染被调方内部实现</li>
 * </ul>
 *
 * <p>DTO 修改必须走严格的版本管理（{@code @since} 标注，重大字段变更新增 v2 版本）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.common.feign.dto;
