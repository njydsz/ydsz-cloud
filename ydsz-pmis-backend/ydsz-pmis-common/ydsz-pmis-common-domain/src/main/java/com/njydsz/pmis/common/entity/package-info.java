/**
 * 通用实体 / 分页查询基类层。
 *
 * <p>定义所有业务实体共用的"地基"：审计字段基类、分页查询入参、游标分页结果等。
 * 业务实体通过继承 {@link com.njydsz.pmis.common.entity.BaseDO} 获得审计字段，
 * 由 {@code AuditFieldFiller}（MyBatis-Plus MetaObjectHandler）自动填充。
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@link com.njydsz.pmis.common.entity.BaseDO}        - 实体基类（createdBy / createdAt / updatedBy / updatedAt / deleted）</li>
 *   <li>{@link com.njydsz.pmis.common.entity.PageQuery}     - 通用分页查询入参（page / size / keyword / orderBy）</li>
 *   <li>{@link com.njydsz.pmis.common.entity.CursorPageQuery} - 游标分页入参（基于 lastId + limit，无 offset 性能问题）</li>
 *   <li>{@link com.njydsz.pmis.common.entity.CursorPageResult} - 游标分页结果（数据 + nextCursor）</li>
 * </ul>
 *
 * <h3>分页策略</h3>
 * <ul>
 *   <li>传统分页（{@code PageQuery}）：适用于后台管理列表，可跳页</li>
 *   <li>游标分页（{@code CursorPageQuery}）：适用于移动端 / 大数据量导出，无 {@code OFFSET} 性能问题</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.common.entity;
