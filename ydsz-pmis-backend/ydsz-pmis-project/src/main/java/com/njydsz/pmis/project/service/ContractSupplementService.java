package com.njydsz.pmis.project.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.project.dto.ContractSupplementDTO;
import com.njydsz.pmis.project.entity.ContractSupplementDO;

import java.util.List;

/**
 * 合同补充协议服务
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface ContractSupplementService {

    Long create(ContractSupplementDTO dto);

    void delete(Long id);

    ContractSupplementDO getById(Long id);

    List<ContractSupplementDO> listByContract(Long contractId);

    Page<ContractSupplementDO> page(int page, int size, Long contractId);
}
