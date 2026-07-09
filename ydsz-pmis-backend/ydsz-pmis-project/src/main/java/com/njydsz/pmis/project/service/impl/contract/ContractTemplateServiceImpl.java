package com.njydsz.pmis.project.service.impl.contract;

import com.njydsz.pmis.common.security.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.project.dto.contract.ContractTemplateCreateDTO;
import com.njydsz.pmis.project.dto.contract.ContractTemplateStatusDTO;
import com.njydsz.pmis.project.entity.contract.ContractTemplateDO;
import com.njydsz.pmis.project.enums.contract.ContractTemplateStatus;
import com.njydsz.pmis.project.enums.contract.ContractTemplateType;
import com.njydsz.pmis.project.mapper.contract.ContractTemplateMapper;
import com.njydsz.pmis.project.service.contract.ContractTemplateService;
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

    /** 合同模板 Mapper */
    private final ContractTemplateMapper templateMapper;

    /**
     * 创建合同模板。
     * <p>默认版本号 1.0.0、默认状态 DRAFT；租户 ID 缺失时填充默认值。</p>
     *
     * @param dto 模板创建参数
     * @return 模板 ID
     * @throws BizException 模板编码重复或参数非法时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(ContractTemplateCreateDTO dto) {
        validate(dto);
        if (templateMapper.selectByCode(dto.getTemplateCode()) != null) {
            throw new BizException(BizErrorCode.DUPLICATE_KEY,
                    "error.project.msg_ba4811d9", dto.getTemplateCode());
        }
        ContractTemplateDO t = new ContractTemplateDO();
        BeanUtils.copyProperties(dto, t);
        if (!StringUtils.hasText(t.getVersion())) t.setVersion("1.0.0");
        if (!StringUtils.hasText(t.getStatus())) t.setStatus(ContractTemplateStatus.DRAFT.getCode());
        if (t.getTenantId() == null) t.setTenantId(TenantContext.getTenantId());
        templateMapper.insert(t);
        log.info("[ContractTemplate] 创建模板: code={} type={}",
                t.getTemplateCode(), t.getContractType());
        return t.getId();
    }

    /**
     * 模板状态迁移（遵循 ContractTemplateStatus 状态机）。
     * <p>PUBLISHED → DRAFT 视为重新编辑，仍允许。</p>
     *
     * @param dto 状态迁移参数
     * @throws BizException 模板不存在、目标状态未知或迁移路径非法时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(ContractTemplateStatusDTO dto) {
        ContractTemplateDO t = getById(dto.getId());
        ContractTemplateStatus from = ContractTemplateStatus.fromCode(t.getStatus());
        ContractTemplateStatus to = ContractTemplateStatus.fromCode(dto.getTargetStatus());
        if (to == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.project.msg_7bc741c6", dto.getTargetStatus());
        }
        if (from == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.project.msg_2e33226a", t.getStatus());
        }
        if (!from.canTransitTo(to)) {
            throw new BizException(BizErrorCode.BAD_REQUEST,
                    "error.project.msg_01c65a70", from.getDesc(), to.getDesc());
        }
        // PUBLISHED -> DRAFT 视为重新编辑（仍允许）
        templateMapper.updateStatus(t.getId(), to.getCode());
        log.info("[ContractTemplate] 状态迁移: id={} {} -> {}", t.getId(), from.getCode(), to.getCode());
    }

    /**
     * 删除模板（逻辑删除）。
     * <p>已发布（PUBLISHED）模板不能直接删除，需先下线。</p>
     *
     * @param id 模板 ID
     * @throws BizException 模板不存在或处于已发布状态时抛出
     */
    @Override
    public void delete(String id) {
        ContractTemplateDO t = getById(id);
        ContractTemplateStatus st = ContractTemplateStatus.fromCode(t.getStatus());
        if (st == ContractTemplateStatus.PUBLISHED) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.project.msg_0b4fd49f");
        }
        templateMapper.deleteById(id);
        log.info("[ContractTemplate] 删除模板: id={}", id);
    }

    /**
     * 根据模板 ID 查询模板详情。
     *
     * @param id 模板 ID
     * @return 模板实体
     * @throws BizException 模板不存在时抛出
     */
    @Override
    @Transactional(readOnly = true)
    public ContractTemplateDO getById(String id) {
        ContractTemplateDO t = templateMapper.selectById(id);
        if (t == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "error.project.msg_e8185aa1");
        }
        return t;
    }

    /**
     * 分页查询合同模板，按创建时间倒序。
     *
     * @param page         页码（从 1 开始）
     * @param size         每页大小
     * @param keyword      关键词（编码/名称），可空
     * @param contractType 合同类型，可空
     * @param status       模板状态，可空
     * @return 分页结果
     */
    @Override
    @Transactional(readOnly = true)
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

    /**
     * 按合同类型查询模板列表。
     *
     * @param contractType 合同类型，可空
     * @param status       模板状态，可空
     * @return 模板列表
     */
    @Override
    @Transactional(readOnly = true)
    public List<ContractTemplateDO> listByType(String contractType, String status) {
        return templateMapper.selectByType(contractType, status);
    }

    /**
     * 校验合同模板创建参数。
     *
     * @param dto 模板创建参数
     * @throws BizException 参数为空、合同类型非法、账期为负或违约金比例越界时抛出
     */
    private void validate(ContractTemplateCreateDTO dto) {
        if (dto == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.project.msg_d9712a58");
        }
        if (ContractTemplateType.fromCode(dto.getContractType()) == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.project.msg_d8bb22ac", dto.getContractType());
        }
        if (dto.getDefaultPaymentDays() != null && dto.getDefaultPaymentDays() < 0) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.project.msg_435fcf5a");
        }
        if (dto.getDefaultPenaltyRate() != null) {
            BigDecimal r = dto.getDefaultPenaltyRate();
            if (r.signum() < 0 || r.compareTo(BigDecimal.ONE) > 0) {
                throw new BizException(BizErrorCode.BAD_REQUEST, "error.project.msg_200cb0f7");
            }
        }
    }
}
