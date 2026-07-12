/**
 * 名称装配器（Name Assembler）。
 *
 * <p>本包集中处理跨服务（userinfo / execution 等）的"ID -> 名称"反查能力，
 * 解决业务实体（DO/VO）只有 {@code ownerId}、{@code customerId}、{@code handlerId}
 * 等外键而无中文名称展示的问题，避免在 Service / Controller 层重复编写 Feign 拉取与降级逻辑。
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@link com.njydsz.pmis.project.server.assembler.NameAssembler} - 员工/客户/供应商等名称的批量解析与降级</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li><b>统一降级</b>：Feign 调用失败一律返回 {@code null} 或空串，不抛异常上抛业务层</li>
 *   <li><b>幂等</b>：相同 ID 重复调用结果一致，无副作用，可放心在循环内调用</li>
 *   <li><b>可替换</b>：未来若引入本地缓存或独立 BFF 聚合，可在本包内替换实现而不影响上层</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>禁止在 Service 中直接调用 Feign 拉取名称，必须经由本包 {@code NameAssembler}</li>
 *   <li>名称解析失败时仅记录 WARN 日志，禁止打断业务主流程</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.project.server.assembler;
