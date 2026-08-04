package com.njydsz.project.server.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.project.domain.entity.project.ProjectContractTemplate;
/**
 * 合同模板 Service
 *
 * <p>管理合同模板（{@code ydsz_project_contract_template}）的维护与应用。</p>
 * <p>合同模板是合同正文的母版，定义了标准条款（交付/付款/验收/保密/违约等），</p>
 * <p>合同签约时基于模板填充具体参数，规避法律风险、提升签约效率。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD：getById / page / save / updateById / removeById</b></li>
 *   <li><b>模板版本：模板变更保留历史版本</b></li>
 *   <li><b>模板应用：合同签约时引用模板生成正文</b></li>
 * </ul>
 *
 * <p><b>模板类型：</b>通用销售合同 / 外协采购合同 / 框架协议 / NDA / 补充协议。
 * <p><b>模板变量：</b>合同金额/项目名称/客户名称/账期等支持 ${var} 占位符。
 *
 * <p><b>事务：</b>所有写操作开启 {@code @Transactional(rollbackFor = Exception.class)}。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.project.domain.entity.project.ProjectContractTemplate 合同模板实体
 * @see ProjectContractService 合同 Service(签约时引用模板)
 */
public interface ProjectContractTemplateService {
    ProjectContractTemplate getById(String id);
    IPage<ProjectContractTemplate> page(int pageNum, int pageSize);
    boolean save(ProjectContractTemplate entity);
    boolean updateById(ProjectContractTemplate entity);
    boolean removeById(String id);
}
