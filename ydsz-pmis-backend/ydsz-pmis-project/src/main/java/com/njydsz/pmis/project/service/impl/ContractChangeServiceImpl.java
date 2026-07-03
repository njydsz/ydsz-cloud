package com.njydsz.pmis.project.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.project.dto.ContractChangeDTO;
import com.njydsz.pmis.project.engine.ContractRiskEvaluator;
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

    /** 允许的变更类型集合：范围/金额/期限/人员/进度 */
    private static final Set<String> CHANGE_TYPES =
            Set.of("SCOPE", "AMOUNT", "TERM", "PERSONNEL", "PROGRESS");

    /** 合同变更 Mapper */
    private final ContractChangeMapper changeMapper;
    /** 合同 Mapper（用于校验合同存在性并联动主合同金额/风险） */
    private final ContractMapper contractMapper;

    /**
     * 提交合同变更申请。
     * <p>处理流程：参数校验 → 合同存在性校验 → 编号唯一性预检 →
     * 属性拷贝 → 默认状态 DRAFT → 持久化。</p>
     *
     * @param dto 变更申请参数
     * @return 变更记录 ID
     * @throws BizException 合同不存在、编号重复或参数非法时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long apply(ContractChangeDTO dto) {
        validate(dto);
        if (contractMapper.selectById(dto.getContractId()) == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "error.project.msg_22d39b90");
        }
        if (changeMapper.selectByCode(dto.getChangeCode()) != null) {
            throw new BizException(BizErrorCode.DUPLICATE_KEY, "error.project.msg_08a1df2a");
        }
        ContractChangeDO c = new ContractChangeDO();
        BeanUtils.copyProperties(dto, c);
        c.setStatus("DRAFT");
        if (c.getTenantId() == null) c.setTenantId(1L);
        changeMapper.insert(c);
        log.info("[ContractChange] 提交变更: code={} type={}", c.getChangeCode(), c.getChangeType());
        return c.getId();
    }

    /**
     * 提交变更进入审批流。
     *
     * @param id 变更 ID
     * @throws BizException 变更不存在或当前状态非 DRAFT 时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        ContractChangeDO c = getById(id);
        if (!"DRAFT".equalsIgnoreCase(c.getStatus())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.project.msg_d85e77c2" + c.getStatus());
        }
        changeMapper.updateStatus(id, "SUBMITTED", null, null);
        log.info("[ContractChange] 提交审批: id={}", id);
    }

    /**
     * 审批通过。
     * <p>金额类型变更会联动调整主合同 totalAmount；同时重新评估主合同风险等级。</p>
     *
     * @param id           变更 ID
     * @param approverId   审批人 ID
     * @param approverName 审批人名称
     * @throws BizException 变更不存在或当前状态不允许审批时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id, Long approverId, String approverName) {
        ContractChangeDO c = getById(id);
        if (!("SUBMITTED".equalsIgnoreCase(c.getStatus()) || "APPROVING".equalsIgnoreCase(c.getStatus()))) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.project.msg_8a0e5737" + c.getStatus());
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
                    ContractRiskEvaluator.evaluate(contract).name());
            contractMapper.updateById(contract);
        }
        log.info("[ContractChange] 审批通过: id={} approver={}", id, approverName);
    }

    /**
     * 驳回变更。
     * <p>驳回原因会追加到 impactAnalysis 字段末尾。</p>
     *
     * @param id           变更 ID
     * @param approverId   审批人 ID
     * @param approverName 审批人名称
     * @param reason       驳回原因，可空
     * @throws BizException 变更不存在或当前状态不允许驳回时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, Long approverId, String approverName, String reason) {
        ContractChangeDO c = getById(id);
        if (!("SUBMITTED".equalsIgnoreCase(c.getStatus()) || "APPROVING".equalsIgnoreCase(c.getStatus()))) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.project.msg_a77d8060" + c.getStatus());
        }
        changeMapper.updateStatus(id, "REJECTED", approverId, approverName);
        if (StringUtils.hasText(reason)) {
            c.setImpactAnalysis((c.getImpactAnalysis() == null ? "" : c.getImpactAnalysis() + "\n")
                    + "驳回原因: " + reason);
            changeMapper.updateById(c);
        }
        log.info("[ContractChange] 驳回: id={} approver={} reason={}", id, approverName, reason);
    }

    /**
     * 根据变更 ID 查询变更详情。
     *
     * @param id 变更 ID
     * @return 变更实体
     * @throws BizException 变更不存在时抛出
     */
    @Override
    public ContractChangeDO getById(Long id) {
        ContractChangeDO c = changeMapper.selectById(id);
        if (c == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "error.project.msg_49023973");
        }
        return c;
    }

    /**
     * 分页查询合同变更列表，按创建时间倒序。
     *
     * @param page       页码（从 1 开始）
     * @param size       每页大小
     * @param contractId 合同 ID，可空
     * @param status     状态码，可空
     * @return 分页结果
     */
    @Override
    public Page<ContractChangeDO> page(int page, int size, Long contractId, String status) {
        Page<ContractChangeDO> p = new Page<>(page, size);
        LambdaQueryWrapper<ContractChangeDO> w = new LambdaQueryWrapper<>();
        if (contractId != null) w.eq(ContractChangeDO::getContractId, contractId);
        if (StringUtils.hasText(status)) w.eq(ContractChangeDO::getStatus, status);
        w.orderByDesc(ContractChangeDO::getCreatedAt);
        return changeMapper.selectPage(p, w);
    }

    /**
     * 按合同查询变更记录列表。
     *
     * @param contractId 合同 ID
     * @return 变更记录列表，合同 ID 为空时返回空列表
     */
    @Override
    public List<ContractChangeDO> listByContract(Long contractId) {
        if (contractId == null) return List.of();
        return changeMapper.selectByContractId(contractId);
    }

    /**
     * 校验合同变更申请参数。
     *
     * @param dto 变更申请参数
     * @throws BizException 参数为空、合同 ID 缺失、变更编号缺失或变更类型非法时抛出
     */
    private void validate(ContractChangeDTO dto) {
        if (dto == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.project.msg_d9712a58");
        }
        if (dto.getContractId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.project.msg_af96cf73");
        }
        if (!StringUtils.hasText(dto.getChangeCode())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.project.msg_00a4ec00");
        }
        if (!CHANGE_TYPES.contains(dto.getChangeType().toUpperCase())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.project.msg_b246fa8c" + dto.getChangeType());
        }
    }
}
