package com.njydsz.pmis.project.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.project.dto.ContractSupplementDTO;
import com.njydsz.pmis.project.entity.ContractDO;
import com.njydsz.pmis.project.entity.ContractSupplementDO;
import com.njydsz.pmis.project.mapper.ContractMapper;
import com.njydsz.pmis.project.mapper.ContractSupplementMapper;
import com.njydsz.pmis.project.service.ContractSupplementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * 合同补充协议服务实现
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContractSupplementServiceImpl implements ContractSupplementService {

    private static final Set<String> TYPES = Set.of("AMOUNT", "SCOPE", "TERM", "OTHER");

    private final ContractSupplementMapper supplementMapper;
    private final ContractMapper contractMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(ContractSupplementDTO dto) {
        validate(dto);
        if (contractMapper.selectById(dto.getContractId()) == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "合同不存在");
        }
        if (supplementMapper.selectByCode(dto.getSupplementCode()) != null) {
            throw new BizException(BizErrorCode.DUPLICATE_KEY, "补充协议编号已存在");
        }
        ContractSupplementDO s = new ContractSupplementDO();
        BeanUtils.copyProperties(dto, s);
        if (!StringUtils.hasText(s.getStatus())) s.setStatus("DRAFT");
        if (s.getTenantId() == null) s.setTenantId(1L);
        supplementMapper.insert(s);

        // 联动：金额类型补充协议直接调整主合同金额
        if ("AMOUNT".equalsIgnoreCase(s.getSupplementType())
                && s.getChangeAmount() != null
                && s.getChangeAmount().signum() != 0) {
            contractMapper.adjustTotalAmount(dto.getContractId(), s.getChangeAmount());
            ContractDO refreshed = contractMapper.selectById(dto.getContractId());
            if (refreshed != null) {
                s.setNewTotalAmount(refreshed.getTotalAmount());
                supplementMapper.updateById(s);
            }
            log.info("[Supplement] 调整主合同 {} 金额 delta={}",
                    dto.getContractId(), s.getChangeAmount());
        }
        log.info("[Supplement] 创建补充协议: code={} type={}", s.getSupplementCode(), s.getSupplementType());
        return s.getId();
    }

    @Override
    public void delete(Long id) {
        ContractSupplementDO s = supplementMapper.selectById(id);
        if (s == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "补充协议不存在");
        }
        supplementMapper.deleteById(id);
    }

    @Override
    public ContractSupplementDO getById(Long id) {
        ContractSupplementDO s = supplementMapper.selectById(id);
        if (s == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "补充协议不存在");
        }
        return s;
    }

    @Override
    public List<ContractSupplementDO> listByContract(Long contractId) {
        if (contractId == null) return List.of();
        return supplementMapper.selectByContractId(contractId);
    }

    @Override
    public Page<ContractSupplementDO> page(int page, int size, Long contractId) {
        Page<ContractSupplementDO> p = new Page<>(page, size);
        LambdaQueryWrapper<ContractSupplementDO> w = new LambdaQueryWrapper<>();
        if (contractId != null) w.eq(ContractSupplementDO::getContractId, contractId);
        w.orderByDesc(ContractSupplementDO::getCreatedAt);
        return supplementMapper.selectPage(p, w);
    }

    private void validate(ContractSupplementDTO dto) {
        if (dto == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "请求不能为空");
        }
        if (dto.getContractId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "合同 ID 不能为空");
        }
        if (!StringUtils.hasText(dto.getSupplementCode())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "补充协议编号不能为空");
        }
        if (!StringUtils.hasText(dto.getSupplementName())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "补充协议名称不能为空");
        }
        if (!TYPES.contains(dto.getSupplementType().toUpperCase())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "类型非法: " + dto.getSupplementType());
        }
    }
}
