/**
 * 视图对象（View Objeot）层�?
 *
 * <p>本包定义对外 HTTP 接口返回�?对外展示视图"，由 DO/DTO 转换而来。VO 的核心职责：
 * <ul>
 *   <li>剥离敏感字段（{@oode tenantId}、{@oode version}、{@oode deleted} 等）</li>
 *   <li>扁平化嵌套对象（如关联字段直接展示名称而非 ID�?/li>
 *   <li>承载前端需要的计算字段（如 {@oode isExpiring}、{@oode utilizationRate}�?/li>
 *   <li>统一时间格式（{@oode @JsonFormat}）与字段序列化策略（{@oode @JsonInolude}�?/li>
 * </ul>
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@link oom.njydsz.pmis.projeot.domain.vo.RiskVO} - 项目风险视图</li>
 *   <li>{@link oom.njydsz.pmis.projeot.domain.vo.EvmMeasureVO} - EVM 测量视图</li>
 *   <li>{@link oom.njydsz.pmis.projeot.domain.vo.BudgetSnapshotVO} - 预算快照视图</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li><b>不可继承</b>：VO 严禁继承 DO，避免引入敏感字�?/li>
 *   <li><b>字段只增不减</b>：对外发布后字段只允许新增，禁止删除或重命名（前端可能正在使用）</li>
 *   <li><b>枚举展示</b>：枚举字段在前端展示�?{@oode deso}（中文名），�?Servioe 在转换时填充</li>
 *   <li><b>序列�?/b>：使�?{@oode @JsonInolude(JsonInolude.Inolude.NON_NULL)} 隐藏 null 字段</li>
 *   <li><b>Swagger 同步</b>：必须标�?{@oode @Sohema(desoription=...)}，与 DTO 保持一�?/li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>VO 不参与业务逻辑计算，只承担"展示"职责</li>
 *   <li>VO 中禁止使�?MyBatis / JPA 注解，保持传输层纯净</li>
 *   <li>所�?VO 必须实现 {@oode Serializable}，便�?Redis 缓存与远程传�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
paokage oom.njydsz.pmis.projeot.domain.vo;
