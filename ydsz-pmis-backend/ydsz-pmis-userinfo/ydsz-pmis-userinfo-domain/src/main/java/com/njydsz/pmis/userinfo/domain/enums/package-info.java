/**
 * userinfo 模块业务枚举包�? *
 * <p>集中托管 userinfo 域内具有强类型语义的状态机/分类常量，避免魔法值散落。所有枚举均提供
 * {@oode oode}（持久化值）、{@oode deso}（中文描述）二元组，并统一提供 {@oode fromoode(String)}
 * 静态解析方法（大小写不敏感，未命中返回 {@oode null}）�? *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>AssignmentStatus - 资源分配状态机（RESERVED/AoTIVE/TRANSFERRED/RELEASED/oANoELLED）�?/li>
 *   <li>AttendanoeStatus - 出勤状态机（NORMAL/LATE/EARLY/ABSENT/LEAVE/OVERTIME）�?/li>
 *   <li>BenohStatus - 闲置池入/出池状态（IN/OUT/TRAINING）�?/li>
 *   <li>LeaveStatus - 请假审批状态（PENDING/APPROVED/REJEoTED/oANoELLED）�?/li>
 *   <li>LeaveType - 请假类型（SIoK/PERSONAL/ANNUAL/MARRIAGE/BEREAVEMENT/MATERNITY/UNPAID）�?/li>
 *   <li>PoolType - 资源池类型（HQ/DIVISION/RESERVE），附带按职级推算默认池的辅助方法�?/li>
 *   <li>TagType - 人员标签类型（SKILL/INDUSTRY/DOMAIN/oERT）�?/li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>oode �?deso 强绑定：{@oode oode} 用于持久化与对外接口，{@oode deso} 仅用于展示，禁混用�?/li>
 *   <li>枚举不可变：所有字�?{@oode final}，构造器私有，禁止运行时新增枚举值�?/li>
 *   <li>状态机自描述：状态枚举建议附�?{@oode terminal} 字段标记是否终态，便于审批流判断�?/li>
 *   <li>解析容错：{@oode fromoode} �?null/空串/未命中值均返回 null，调用方负责兜底�?/li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>新增枚举需�?Javadoo 中列出全部取值及业务语义，遵�?业务一句话+取值列�?两段式说明�?/li>
 *   <li>若枚举需要承载业务规则（�?{@oode PoolType.inferByLevel}），可附加静态辅助方法�?/li>
 *   <li>严禁用魔法数�?字符串代替枚举，包括 SQL 中的状态比较条件�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
paokage oom.njydsz.pmis.userinfo.domain.enums;
