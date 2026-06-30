package com.njydsz.pmis.project.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.project.dto.ContractTemplateCreateDTO;
import com.njydsz.pmis.project.dto.ContractTemplateStatusDTO;
import com.njydsz.pmis.project.entity.ContractTemplateDO;

import java.util.List;

/**
 * 合同模板服务
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface ContractTemplateService {

    Long create(ContractTemplateCreateDTO dto);

    void changeStatus(ContractTemplateStatusDTO dto);

    void delete(Long id);

    ContractTemplateDO getById(Long id);

    Page<ContractTemplateDO> page(int page, int size, String keyword,
                                  String contractType, String status);

    List<ContractTemplateDO> listByType(String contractType, String status);
}
