package com.njydsz.pmis.project.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.project.assembler.NameAssembler;
import com.njydsz.pmis.project.dto.ContractCreateDTO;
import com.njydsz.pmis.project.dto.ContractStatusDTO;
import com.njydsz.pmis.project.engine.ContractRiskEvaluator;
import com.njydsz.pmis.project.entity.ContractDO;
import com.njydsz.pmis.project.enums.ContractStatus;
import com.njydsz.pmis.project.enums.RiskLevel;
import com.njydsz.pmis.project.mapper.ContractMapper;
import com.njydsz.pmis.project.service.ContractService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 合同服务实现
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContractServiceImpl implements ContractService {

    private final ContractMapper contractMapper;
    private final NameAssembler nameAssembler;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(ContractCreateDTO dto) {
        validate(dto);
        if (contractMapper.selectByCode(dto.getContractCode()) != null) {
            throw new BizException(BizErrorCode.DUPLICATE_KEY, "合同编号已存在: " + dto.getContractCode());
        }
        ContractDO c = new ContractDO();
        BeanUtils.copyProperties(dto, c);
        if (!StringUtils.hasText(c.getStatus())) {
            c.setStatus(ContractStatus.DRAFT.getCode());
        }
        if (!StringUtils.hasText(c.getCurrency())) {
            c.setCurrency("CNY");
        }
        if (c.getTenantId() == null) c.setTenantId(1L);
        // 自动风险评估
        if (!StringUtils.hasText(c.getRiskLevel())) {
            c.setRiskLevel(ContractRiskEvaluator.evaluate(c).name());
        }
        contractMapper.insert(c);
        log.info("[Contract] 创建合同: code={} name={}", c.getContractCode(), c.getContractName());
        return c.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(ContractStatusDTO dto) {
        ContractDO c = getById(dto.getId());
        ContractStatus from = ContractStatus.fromCode(c.getStatus());
        ContractStatus to = ContractStatus.fromCode(dto.getTargetStatus());
        if (to == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "未知状态: " + dto.getTargetStatus());
        }
        if (from == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "当前状态非法: " + c.getStatus());
        }
        if (!from.canTransitTo(to)) {
            throw new BizException(BizErrorCode.BAD_REQUEST,
                    "状态不允许迁移: " + from.getDesc() + " → " + to.getDesc());
        }
        contractMapper.updateStatus(c.getId(), to.getCode());
        log.info("[Contract] 状态迁移: id={} {} -> {}", c.getId(), from.getCode(), to.getCode());
    }

    @Override
    public void delete(Long id) {
        ContractDO c = getById(id);
        contractMapper.deleteById(c.getId());
        log.info("[Contract] 删除合同: id={}", id);
    }

    @Override
    public ContractDO getById(Long id) {
        ContractDO c = contractMapper.selectById(id);
        if (c == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "合同不存在");
        }
        assembleNames(c);
        return c;
    }

    @Override
    public Page<ContractDO> page(int page, int size, String keyword, String status,
                                 String contractType, String riskLevel) {
        Page<ContractDO> p = new Page<>(page, size);
        LambdaQueryWrapper<ContractDO> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            w.and(qw -> qw.like(ContractDO::getContractCode, keyword)
                    .or().like(ContractDO::getContractName, keyword)
                    .or().like(ContractDO::getCustomerName, keyword));
        }
        if (StringUtils.hasText(status)) w.eq(ContractDO::getStatus, status);
        if (StringUtils.hasText(contractType)) w.eq(ContractDO::getContractType, contractType);
        if (StringUtils.hasText(riskLevel)) w.eq(ContractDO::getRiskLevel, riskLevel);
        w.orderByDesc(ContractDO::getCreatedAt);
        Page<ContractDO> result = contractMapper.selectPage(p, w);
        if (result != null && result.getRecords() != null) {
            for (ContractDO rec : result.getRecords()) {
                assembleNames(rec);
            }
        }
        return result;
    }

    @Override
    public String evaluateRisk(Long id) {
        ContractDO c = getById(id);
        RiskLevel level = ContractRiskEvaluator.evaluate(c);
        c.setRiskLevel(level.name());
        contractMapper.updateById(c);
        return level.name();
    }

    @Override
    public List<Map<String, Object>> aggregateByStatus(Long tenantId) {
        if (tenantId == null) tenantId = 1L;
        return contractMapper.aggregateByStatus(tenantId);
    }

    @Override
    public List<Map<String, Object>> aggregateByRisk(Long tenantId) {
        if (tenantId == null) tenantId = 1L;
        return contractMapper.aggregateByRisk(tenantId);
    }

    private void validate(ContractCreateDTO dto) {
        if (dto == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "请求不能为空");
        }
        if (!StringUtils.hasText(dto.getContractCode())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "合同编号不能为空");
        }
        if (!StringUtils.hasText(dto.getContractName())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "合同名称不能为空");
        }
        if (dto.getCustomerId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "客户 ID 不能为空");
        }
        if (!StringUtils.hasText(dto.getContractType())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "合同类型不能为空");
        }
        if (dto.getTotalAmount() == null || dto.getTotalAmount().signum() < 0) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "合同金额必须为非负数");
        }
        if (dto.getOwnerId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "负责人 ID 不能为空");
        }
        if (dto.getEffectiveDate() != null && dto.getExpireDate() != null
                && dto.getExpireDate().isBefore(dto.getEffectiveDate())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "到期日期不能早于生效日期");
        }
    }

    private void assembleNames(ContractDO c) {
        if (c == null || nameAssembler == null) return;
        if (!StringUtils.hasText(c.getCustomerName()) && c.getCustomerId() != null) {
            try {
                String n = nameAssembler.resolveCustomer(c.getCustomerId());
                if (n != null) c.setCustomerName(n);
            } catch (Exception ignore) { }
        }
        if (!StringUtils.hasText(c.getOwnerName()) && c.getOwnerId() != null) {
            try {
                String n = nameAssembler.resolveEmployee(c.getOwnerId());
                if (n != null) c.setOwnerName(n);
            } catch (Exception ignore) { }
        }
    }
}
