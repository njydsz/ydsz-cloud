/**
 * userinfo 模块业务枚举包。
 *
 * <p>集中托管 userinfo 域内具有强类型语义的状态机/分类常量，避免魔法值散落。所有枚举均提供
 * {@code code}（持久化值）、{@code desc}（中文描述）二元组，并统一提供 {@code fromCode(String)}
 * 静态解析方法（大小写不敏感，未命中返回 {@code null}）。
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>AssignmentStatus - 资源分配状态机（RESERVED/ACTIVE/TRANSFERRED/RELEASED/CANCELLED）。</li>
 *   <li>AttendanceStatus - 出勤状态机（NORMAL/LATE/EARLY/ABSENT/LEAVE/OVERTIME）。</li>
 *   <li>BenchStatus - 闲置池入/出池状态（IN/OUT/TRAINING）。</li>
 *   <li>LeaveStatus - 请假审批状态（PENDING/APPROVED/REJECTED/CANCELLED）。</li>
 *   <li>LeaveType - 请假类型（SICK/PERSONAL/ANNUAL/MARRIAGE/BEREAVEMENT/MATERNITY/UNPAID）。</li>
 *   <li>PoolType - 资源池类型（HQ/DIVISION/RESERVE），附带按职级推算默认池的辅助方法。</li>
 *   <li>TagType - 人员标签类型（SKILL/INDUSTRY/DOMAIN/CERT）。</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>code 与 desc 强绑定：{@code code} 用于持久化与对外接口，{@code desc} 仅用于展示，禁混用。</li>
 *   <li>枚举不可变：所有字段 {@code final}，构造器私有，禁止运行时新增枚举值。</li>
 *   <li>状态机自描述：状态枚举建议附带 {@code terminal} 字段标记是否终态，便于审批流判断。</li>
 *   <li>解析容错：{@code fromCode} 对 null/空串/未命中值均返回 null，调用方负责兜底。</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>新增枚举需在 Javadoc 中列出全部取值及业务语义，遵循"业务一句话+取值列表"两段式说明。</li>
 *   <li>若枚举需要承载业务规则（如 {@code PoolType.inferByLevel}），可附加静态辅助方法。</li>
 *   <li>严禁用魔法数字/字符串代替枚举，包括 SQL 中的状态比较条件。</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.userinfo.enums;
