package com.njydsz.pmis.project.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.project.dto.ContractTemplateCreateDTO;
import com.njydsz.pmis.project.dto.ContractTemplateStatusDTO;
import com.njydsz.pmis.project.entity.ContractTemplateDO;
import com.njydsz.pmis.project.enums.ContractTemplateStatus;
import com.njydsz.pmis.project.enums.ContractTemplateType;
import com.njydsz.pmis.project.mapper.ContractTemplateMapper;
import com.njydsz.pmis.project.service.ContractTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;

/**
 * 合同模板服务实现
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContractTemplateServiceImpl implements ContractTemplateService {

    private final ContractTemplateMapper templateMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(ContractTemplateCreateDTO dto) {
        validate(dto);
        if (templateMapper.selectByCode(dto.getTemplateCode()) != null) {
            throw new BizException(BizErrorCode.DUPLICATE_KEY,
                    "模板编码已存在: " + dto.getTemplateCode());
        }
        ContractTemplateDO t = new ContractTemplateDO();
        BeanUtils.copyProperties(dto, t);
        if (!StringUtils.hasText(t.getVersion())) t.setVersion("1.0.0");
        if (!StringUtils.hasText(t.getStatus())) t.setStatus(ContractTemplateStatus.DRAFT.getCode());
        if (t.getTenantId() == null) t.setTenantId(1L);
        templateMapper.insert(t);
        log.info("[ContractTemplate] 创建模板: code={} type={}",
                t.getTemplateCode(), t.getContractType());
        return t.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(ContractTemplateStatusDTO dto) {
        ContractTemplateDO t = getById(dto.getId());
        ContractTemplateStatus from = ContractTemplateStatus.fromCode(t.getStatus());
        ContractTemplateStatus to = ContractTemplateStatus.fromCode(dto.getTargetStatus());
        if (to == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "未知状态: " + dto.getTargetStatus());
        }
        if (from == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "当前状态非法: " + t.getStatus());
        }
        if (!from.canTransitTo(to)) {
            throw new BizException(BizErrorCode.BAD_REQUEST,
                    "状态不允许迁移: " + from.getDesc() + " → " + to.getDesc());
        }
        // PUBLISHED -> DRAFT 视为重新编辑（仍允许）
        templateMapper.updateStatus(t.getId(), to.getCode());
        log.info("[ContractTemplate] 状态迁移: id={} {} -> {}", t.getId(), from.getCode(), to.getCode());
    }

    @Override
    public void delete(Long id) {
        ContractTemplateDO t = getById(id);
        ContractTemplateStatus st = ContractTemplateStatus.fromCode(t.getStatus());
        if (st == ContractTemplateStatus.PUBLISHED) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "已发布模板不能直接删除，请先下线");
        }
        templateMapper.deleteById(id);
        log.info("[ContractTemplate] 删除模板: id={}", id);
    }

    @Override
    public ContractTemplateDO getById(Long id) {
        ContractTemplateDO t = templateMapper.selectById(id);
        if (t == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "合同模板不存在");
        }
        return t;
    }

    @Override
    public Page<ContractTemplateDO> page(int page, int size, String keyword,
                                         String contractType, String status) {
        Page<ContractTemplateDO> p = new Page<>(page, size);
        LambdaQueryWrapper<ContractTemplateDO> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            w.and(qw -> qw.like(ContractTemplateDO::getTemplateCode, keyword)
                    .or().like(ContractTemplateDO::getTemplateName, keyword));
        }
        if (StringUtils.hasText(contractType)) w.eq(ContractTemplateDO::getContractType, contractType);
        if (StringUtils.hasText(status)) w.eq(ContractTemplateDO::getStatus, status);
        w.orderByDesc(ContractTemplateDO::getCreatedAt);
        return templateMapper.selectPage(p, w);
    }

    @Override
    public List<ContractTemplateDO> listByType(String contractType, String status) {
        return templateMapper.selectByType(contractType, status);
    }

    private void validate(ContractTemplateCreateDTO dto) {
        if (dto == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "请求不能为空");
        }
        if (ContractTemplateType.fromCode(dto.getContractType()) == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "合同类型不合法: " + dto.getContractType());
        }
        if (dto.getDefaultPaymentDays() != null && dto.getDefaultPaymentDays() < 0) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "默认账期不能为负数");
        }
        if (dto.getDefaultPenaltyRate() != null) {
            BigDecimal r = dto.getDefaultPenaltyRate();
            if (r.signum() < 0 || r.compareTo(BigDecimal.ONE) > 0) {
                throw new BizException(BizErrorCode.BAD_REQUEST, "违约金比例必须在 [0,1] 之间");
            }
        }
    }
}
