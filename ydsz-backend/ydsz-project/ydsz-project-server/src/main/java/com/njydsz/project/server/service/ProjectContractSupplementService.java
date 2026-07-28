package com.njydsz.project.server.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.project.domain.entity.project.ProjectContractSupplement;
/**
 * 合同补充协议 Service
 *
 * <p>管理合同补充协议（{@code ydsz_project_contract_supplement}）的录入与查询。</p>
 * <p>补充协议是对原合同的补充约定，用于在合同执行过程中调整部分条款（增项/账期/工作范围），</p>
 * <p>与变更（{@link ProjectContractChangeService}）的区别：补充协议是双方新达成的一致；变更是对原条款的修改。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD：getById / page / save / updateById / removeById</b></li>
 *   <li><b>关联主合同：补充协议依附于主合同存在</b></li>
 *   <li><b>金额叠加：补充协议金额计入主合同总额</b></li>
 * </ul>
 *
 * <p><b>与变更区别：</b>补充协议是增量条款，变更是原条款替换。
 *
 * <p><b>事务：</b>所有写操作开启 {@code @Transactional(rollbackFor = Exception.class)}。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.project.domain.entity.project.ProjectContractSupplement 合同补充协议实体
 * @see ProjectContractService 合同 Service(主合同关联)
 * @see ProjectContractChangeService 合同变更 Service(兄弟概念)
 */
public interface ProjectContractSupplementService {
    ProjectContractSupplement getById(String id);
    IPage<ProjectContractSupplement> page(int pageNum, int pageSize);
    boolean save(ProjectContractSupplement entity);
    boolean updateById(ProjectContractSupplement entity);
    boolean removeById(String id);
}
