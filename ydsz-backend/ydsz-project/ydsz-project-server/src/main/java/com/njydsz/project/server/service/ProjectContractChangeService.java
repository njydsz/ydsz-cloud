package com.njydsz.project.server.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.project.domain.entity.project.ProjectContractChange;
/**
 * 合同变更 Service
 *
 * <p>管理合同变更（{@code ydsz_project_contract_change}）的申请、审批、归档。</p>
 * <p>合同变更是对原合同条款的修改（工作范围/金额/工期/账期），需经双方书面确认后生效，</p>
 * <p>属于原条款替换性质。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD：getById / page / save / updateById / removeById</b></li>
 *   <li><b>变更申请：发起合同变更，关联原合同</b></li>
 *   <li><b>变更审批：走 workflow 审批流程</b></li>
 *   <li><b>生效后：原合同相应字段被替换</b></li>
 * </ul>
 *
 * <p><b>变更类型：</b>范围变更 / 金额变更 / 工期变更 / 账期变更。
 * <p><b>与补充协议区别：</b>变更是原条款替换，补充协议是增量条款。
 *
 * <p><b>事务：</b>所有写操作开启 {@code @Transactional(rollbackFor = Exception.class)}。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.project.domain.entity.project.ProjectContractChange 合同变更实体
 * @see ProjectContractService 合同 Service(被变更的主合同)
 * @see ProjectContractSupplementService 合同补充协议 Service(兄弟概念)
 */
public interface ProjectContractChangeService {
    ProjectContractChange getById(String id);
    IPage<ProjectContractChange> page(int pageNum, int pageSize);
    boolean save(ProjectContractChange entity);
    boolean updateById(ProjectContractChange entity);
    boolean removeById(String id);
}
