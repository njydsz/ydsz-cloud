/**
 * 财务会计服务 — 领域层
 *
 * <p>包含实体（Entity）、数据传输对象（DTO）、枚举（Enum）、值对象（VO）、
 * 查询对象（Query）和类型转换器（Converter）。
 *
 * <h2>领域划分</h2>
 * <ul>
 *   <li>{@code entity} — InvoiceDO / PaymentDO / ExpenseDO / RevenueDO / ProfitSnapshotDO / ProfitSimulationDO / CustomerCreditDO / DailyReconcileDO</li>
 *   <li>{@code dto} — InvoiceCreateDTO / PaymentCreateDTO / ExpenseCreateDTO / RevenueCreateDTO / ProfitSimulationCreateDTO 等</li>
 *   <li>{@code enums} — InvoiceStatus / InvoiceType / PaymentStatus / CreditLevel / ReconcileType / RevenueRecognitionMethod 等</li>
 * </ul>
 *
 * <h2>设计约束</h2>
 * <ul>
 *   <li>领域层不依赖 infra / server / web 层，可独立编译</li>
 *   <li>实体类使用 MyBatis-Plus 注解（{@code @TableName}/{@code @TableId}），但不依赖 Mapper</li>
 *   <li>DTO 按操作语义命名：CreateDTO（POST）/ StatusDTO（PUT 状态变更）/ ApprovalDTO（审批）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
package com.njydsz.pmis.finance.domain;
