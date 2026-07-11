/**
 * 视图对象（View Object）层。
 *
 * <p>本包定义对外 HTTP 接口返回的"对外展示视图"，由 DO/DTO 转换而来。VO 的核心职责：
 * <ul>
 *   <li>剥离敏感字段（{@code tenantId}、{@code version}、{@code deleted} 等）</li>
 *   <li>扁平化嵌套对象（如关联字段直接展示名称而非 ID）</li>
 *   <li>承载前端需要的计算字段（如 {@code isExpiring}、{@code utilizationRate}）</li>
 *   <li>统一时间格式（{@code @JsonFormat}）与字段序列化策略（{@code @JsonInclude}）</li>
 * </ul>
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@link com.njydsz.pmis.project.domain.vo.RiskVO} - 项目风险视图</li>
 *   <li>{@link com.njydsz.pmis.project.domain.vo.EvmMeasureVO} - EVM 测量视图</li>
 *   <li>{@link com.njydsz.pmis.project.domain.vo.BudgetSnapshotVO} - 预算快照视图</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li><b>不可继承</b>：VO 严禁继承 DO，避免引入敏感字段</li>
 *   <li><b>字段只增不减</b>：对外发布后字段只允许新增，禁止删除或重命名（前端可能正在使用）</li>
 *   <li><b>枚举展示</b>：枚举字段在前端展示为 {@code desc}（中文名），由 Service 在转换时填充</li>
 *   <li><b>序列化</b>：使用 {@code @JsonInclude(JsonInclude.Include.NON_NULL)} 隐藏 null 字段</li>
 *   <li><b>Swagger 同步</b>：必须标注 {@code @Schema(description=...)}，与 DTO 保持一致</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>VO 不参与业务逻辑计算，只承担"展示"职责</li>
 *   <li>VO 中禁止使用 MyBatis / JPA 注解，保持传输层纯净</li>
 *   <li>所有 VO 必须实现 {@code Serializable}，便于 Redis 缓存与远程传输</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.project.domain.vo;
