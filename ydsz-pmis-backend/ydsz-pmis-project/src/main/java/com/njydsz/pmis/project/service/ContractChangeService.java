package com.njydsz.pmis.project.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.project.dto.ContractChangeDTO;
import com.njydsz.pmis.project.entity.ContractChangeDO;

import java.util.List;

/**
 * 合同变更服务
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface ContractChangeService {

    Long apply(ContractChangeDTO dto);

    void submit(Long id);

    void approve(Long id, Long approverId, String approverName);

    void reject(Long id, Long approverId, String approverName, String reason);

    ContractChangeDO getById(Long id);

    Page<ContractChangeDO> page(int page, int size, Long contractId, String status);

    List<ContractChangeDO> listByContract(Long contractId);
}
