package com.njydsz.project.server.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.project.domain.entity.execution.ExecutionDeliveryItem;
/**
 * 项目交付物 Service
 *
 * <p>管理项目交付物（{@code ydsz_execution_delivery_item}）的登记、确认、归档。</p>
 * <p>交付物是项目产出的具体物件（代码/文档/数据/系统/培训等），按交付标准验收，</p>
 * <p>验收通过后归档作为客户资产。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD：getById / page / save / updateById / removeById</b></li>
 *   <li><b>交付物登记：按交付标准登记交付物</b></li>
 *   <li><b>客户验收：客户在系统中确认验收</b></li>
 *   <li><b>归档：验收通过后归档</b></li>
 * </ul>
 *
 * <p><b>交付物类型：</b>代码 / 文档 / 数据 / 系统 / 培训 / 其他。
 * <p><b>验收状态：</b>PENDING / DELIVERED / ACCEPTED / REJECTED / ARCHIVED。
 *
 * <p><b>事务：</b>所有写操作开启 {@code @Transactional(rollbackFor = Exception.class)}。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.project.domain.entity.execution.ExecutionDeliveryItem 交付物实体
 * @see ExecutionDeliveryStandardService 交付标准 Service(对照标准)
 * @see ExecutionClosureService 项目终验 Service(终验后归档)
 */
public interface ExecutionDeliveryItemService {
    ExecutionDeliveryItem getById(String id);
    IPage<ExecutionDeliveryItem> page(int pageNum, int pageSize);
    boolean save(ExecutionDeliveryItem entity);
    boolean updateById(ExecutionDeliveryItem entity);
    boolean removeById(String id);
}
