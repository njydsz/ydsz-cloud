package com.njydsz.pmis.project.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.project.dto.ContractChangeDTO;
import com.njydsz.pmis.project.entity.ContractChangeDO;
import com.njydsz.pmis.project.entity.ContractDO;
import com.njydsz.pmis.project.mapper.ContractChangeMapper;
import com.njydsz.pmis.project.mapper.ContractMapper;
import com.njydsz.pmis.project.service.ContractChangeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

/**
 * 合同变更服务实现
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContractChangeServiceImpl implements ContractChangeService {

    private static final Set<String> CHANGE_TYPES =
            Set.of("SCOPE", "AMOUNT", "TERM", "PERSONNEL", "PROGRESS");

    private final ContractChangeMapper changeMapper;
    private final ContractMapper contractMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long apply(ContractChangeDTO dto) {
        validate(dto);
        if (contractMapper.selectById(dto.getContractId()) == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "合同不存在");
        }
        if (changeMapper.selectByCode(dto.getChangeCode()) != null) {
            throw new BizException(BizErrorCode.DUPLICATE_KEY, "变更编号已存在");
        }
        ContractChangeDO c = new ContractChangeDO();
        BeanUtils.copyProperties(dto, c);
        c.setStatus("DRAFT");
        if (c.getTenantId() == null) c.setTenantId(1L);
        changeMapper.insert(c);
        log.info("[ContractChange] 提交变更: code={} type={}", c.getChangeCode(), c.getChangeType());
        return c.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        ContractChangeDO c = getById(id);
        if (!"DRAFT".equalsIgnoreCase(c.getStatus())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "只有 DRAFT 可以提交: " + c.getStatus());
        }
        changeMapper.updateStatus(id, "SUBMITTED", null, null);
        log.info("[ContractChange] 提交审批: id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id, Long approverId, String approverName) {
        ContractChangeDO c = getById(id);
        if (!("SUBMITTED".equalsIgnoreCase(c.getStatus()) || "APPROVING".equalsIgnoreCase(c.getStatus()))) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "当前状态不允许审批: " + c.getStatus());
        }
        changeMapper.updateStatus(id, "APPROVED", approverId, approverName);

        // 联动主合同：如果变更涉及金额，调整合同金额
        if ("AMOUNT".equalsIgnoreCase(c.getChangeType()) && c.getAmountDelta() != null
                && c.getAmountDelta().signum() != 0) {
            contractMapper.adjustTotalAmount(c.getContractId(), c.getAmountDelta());
            log.info("[ContractChange] 联动主合同 {} 金额 delta={}", c.getContractId(), c.getAmountDelta());
        }
        // 重新评估风险
        ContractDO contract = contractMapper.selectById(c.getContractId());
        if (contract != null) {
            contract.setRiskLevel(
                    com.njydsz.pmis.project.engine.ContractRiskEvaluator.evaluate(contract).name());
            contractMapper.updateById(contract);
        }
        log.info("[ContractChange] 审批通过: id={} approver={}", id, approverName);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, Long approverId, String approverName, String reason) {
        ContractChangeDO c = getById(id);
        if (!("SUBMITTED".equalsIgnoreCase(c.getStatus()) || "APPROVING".equalsIgnoreCase(c.getStatus()))) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "当前状态不允许驳回: " + c.getStatus());
        }
        changeMapper.updateStatus(id, "REJECTED", approverId, approverName);
        if (StringUtils.hasText(reason)) {
            c.setImpactAnalysis((c.getImpactAnalysis() == null ? "" : c.getImpactAnalysis() + "\n")
                    + "驳回原因: " + reason);
            changeMapper.updateById(c);
        }
        log.info("[ContractChange] 驳回: id={} approver={} reason={}", id, approverName, reason);
    }

    @Override
    public ContractChangeDO getById(Long id) {
        ContractChangeDO c = changeMapper.selectById(id);
        if (c == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "变更记录不存在");
        }
        return c;
    }

    @Override
    public Page<ContractChangeDO> page(int page, int size, Long contractId, String status) {
        Page<ContractChangeDO> p = new Page<>(page, size);
        LambdaQueryWrapper<ContractChangeDO> w = new LambdaQueryWrapper<>();
        if (contractId != null) w.eq(ContractChangeDO::getContractId, contractId);
        if (StringUtils.hasText(status)) w.eq(ContractChangeDO::getStatus, status);
        w.orderByDesc(ContractChangeDO::getCreatedAt);
        return changeMapper.selectPage(p, w);
    }

    @Override
    public List<ContractChangeDO> listByContract(Long contractId) {
        if (contractId == null) return List.of();
        return changeMapper.selectByContractId(contractId);
    }

    private void validate(ContractChangeDTO dto) {
        if (dto == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "请求不能为空");
        }
        if (dto.getContractId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "合同 ID 不能为空");
        }
        if (!StringUtils.hasText(dto.getChangeCode())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "变更编号不能为空");
        }
        if (!CHANGE_TYPES.contains(dto.getChangeType().toUpperCase())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "变更类型非法: " + dto.getChangeType());
        }
    }
}
