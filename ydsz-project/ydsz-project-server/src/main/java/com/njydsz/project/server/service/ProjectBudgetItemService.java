package com.njydsz.project.server.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.project.domain.entity.project.ProjectBudgetItem;
/**
 * 项目预算项 Service
 *
 * <p>管理项目预算项（{@code ydsz_project_budget_item}）的录入与查询。</p>
 * <p>预算项是项目预算的最小单元，按科目（工时/采购/差旅/外协/管理费等）维度编制，</p>
 * <p>用于控制项目支出、对比实际成本、利润分析。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD：getById / page / save / updateById / removeById</b></li>
 *   <li><b>预算项按科目：工时/采购/差旅/外协/招待/管理费/其他</b></li>
 *   <li><b>预算版本：每次预算调整生成新版本</b></li>
 * </ul>
 *
 * <p><b>预算项字段：</b>科目 / 预算金额 / 已用金额 / 剩余金额 / 责任人。
 *
 * <p><b>事务：</b>所有写操作开启 {@code @Transactional(rollbackFor = Exception.class)}。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.project.domain.entity.project.ProjectBudgetItem 预算项实体
 * @see ProjectInitiationService 立项 Service(立项时编制预算)
 * @see CostAllocationService 成本分摊 Service(实际成本对比)
 */
public interface ProjectBudgetItemService {
    ProjectBudgetItem getById(String id);
    IPage<ProjectBudgetItem> page(int pageNum, int pageSize);
    boolean save(ProjectBudgetItem entity);
    boolean updateById(ProjectBudgetItem entity);
    boolean removeById(String id);
}
