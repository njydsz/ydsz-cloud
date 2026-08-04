package com.njydsz.project.server.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.project.domain.entity.cost.CostPurchase;
/**
 * 采购成本 Service
 *
 * <p>管理项目采购成本（{@code ydsz_cost_purchase}）的登记、审批、入账。</p>
 * <p>采购成本是项目非工时成本的主要组成（外协/采购/外包等），按订单/合同/验收节点分摊到项目，</p>
 * <p>计入项目成本与利润。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD：getById / page / save / updateById / removeById</b></li>
 *   <li><b>采购订单：关联采购订单/采购合同</b></li>
 *   <li><b>验收入库：采购到货验收后入账</b></li>
 *   <li><b>分摊到项目：按项目维度分摊</b></li>
 * </ul>
 *
 * <p><b>采购类型：</b>硬件采购 / 软件采购 / 外协服务 / 外包人力。
 *
 * <p><b>事务：</b>所有写操作开启 {@code @Transactional(rollbackFor = Exception.class)}。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.project.domain.entity.cost.CostPurchase 采购成本实体
 * @see ProjectExpenseService 项目费用 Service(费用与采购成本互补)
 * @see CostAllocationService 成本分摊 Service(分摊到项目)
 */
public interface CostPurchaseService {
    CostPurchase getById(String id);
    IPage<CostPurchase> page(int pageNum, int pageSize);
    boolean save(CostPurchase entity);
    boolean updateById(CostPurchase entity);
    boolean removeById(String id);
}
