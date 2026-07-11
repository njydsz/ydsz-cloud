/**
 * 商务销售服务 — 领域层
 *
 * <p>包含实体（Entity）、数据传输对象（DTO）、枚举（Enum）、值对象（VO）、
 * 查询对象（Query）和类型转换器（Converter）。
 *
 * <h2>领域划分</h2>
 * <ul>
 *   <li>{@code entity} — OpportunityDO / ContractDO / ContractChangeDO / ContractSupplementDO / ContractTemplateDO</li>
 *   <li>{@code dto} — OpportunityCreateDTO / ContractCreateDTO / ContractChangeDTO / ContractStatusDTO 等</li>
 *   <li>{@code enums} — OpportunityStatus / OpportunityLevel / ContractStatus / ContractTemplateStatus 等</li>
 * </ul>
 *
 * <h2>设计约束</h2>
 * <ul>
 *   <li>领域层不依赖 infra / server / web 层，可独立编译</li>
 *   <li>实体类使用 MyBatis-Plus 注解（{@code @TableName}/{@code @TableId}），但不依赖 Mapper</li>
 *   <li>DTO 按操作语义命名：CreateDTO（POST）/ StatusDTO（PUT 状态变更）/ UpdateDTO（PUT 全量更新）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
package com.njydsz.pmis.sales.domain;
