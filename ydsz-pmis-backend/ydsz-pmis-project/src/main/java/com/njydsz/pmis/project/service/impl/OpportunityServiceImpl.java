package com.njydsz.pmis.project.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.project.dto.OpportunityCreateDTO;
import com.njydsz.pmis.project.dto.OpportunityStatusDTO;
import com.njydsz.pmis.project.dto.OpportunityUpdateDTO;
import com.njydsz.pmis.project.engine.WinRateEvaluator;
import com.njydsz.pmis.project.entity.OpportunityDO;
import com.njydsz.pmis.project.enums.OpportunityStatus;
import com.njydsz.pmis.project.mapper.OpportunityMapper;
import com.njydsz.pmis.project.service.OpportunityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 商机服务实现
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpportunityServiceImpl implements OpportunityService {

    private final OpportunityMapper opportunityMapper;
    private final com.njydsz.pmis.project.assembler.NameAssembler nameAssembler;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(OpportunityCreateDTO dto) {
        validate(dto);
        if (opportunityMapper.selectByCode(dto.getOpportunityCode()) != null) {
            throw new BizException(BizErrorCode.DUPLICATE_KEY,
                    "商机编号已存在: " + dto.getOpportunityCode());
        }
        OpportunityDO o = new OpportunityDO();
        BeanUtils.copyProperties(dto, o);
        if (!StringUtils.hasText(o.getStatus())) {
            o.setStatus(OpportunityStatus.FOLLOWING.getCode());
        }
        if (!StringUtils.hasText(o.getLevel())) {
            o.setLevel("C");
        }
        if (o.getTenantId() == null) {
            o.setTenantId(1L);
        }
        if (o.getWinRate() == null) {
            o.setWinRate(WinRateEvaluator.evaluate(o));
        }
        // 装配客户/负责人名称（容错）
        if (!StringUtils.hasText(o.getCustomerName())) {
            String n = safeCustomerName(o.getCustomerId());
            if (n != null) o.setCustomerName(n);
        }
        if (!StringUtils.hasText(o.getOwnerName())) {
            String n = safeEmployeeName(o.getOwnerId());
            if (n != null) o.setOwnerName(n);
        }
        opportunityMapper.insert(o);
        log.info("[Opportunity] 创建商机: code={} name={}", o.getOpportunityCode(), o.getOpportunityName());
        return o.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(OpportunityUpdateDTO dto) {
        if (dto.getId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "商机 ID 不能为空");
        }
        OpportunityDO o = opportunityMapper.selectById(dto.getId());
        if (o == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "商机不存在");
        }
        if (StringUtils.hasText(dto.getOpportunityName())) o.setOpportunityName(dto.getOpportunityName());
        if (StringUtils.hasText(dto.getLevel())) o.setLevel(dto.getLevel());
        if (StringUtils.hasText(dto.getIndustry())) o.setIndustry(dto.getIndustry());
        if (dto.getEstimatedAmount() != null) o.setEstimatedAmount(dto.getEstimatedAmount());
        if (dto.getWinRate() != null) o.setWinRate(dto.getWinRate());
        if (dto.getExpectedSignDate() != null) o.setExpectedSignDate(dto.getExpectedSignDate());
        if (dto.getExpectedStartDate() != null) o.setExpectedStartDate(dto.getExpectedStartDate());
        if (dto.getExpectedEndDate() != null) o.setExpectedEndDate(dto.getExpectedEndDate());
        if (dto.getCompetitor() != null) o.setCompetitor(dto.getCompetitor());
        if (dto.getRemark() != null) o.setRemark(dto.getRemark());
        if (dto.getTags() != null) o.setTags(dto.getTags());
        opportunityMapper.updateById(o);
        log.info("[Opportunity] 更新商机: id={}", o.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(OpportunityStatusDTO dto) {
        OpportunityDO o = getById(dto.getId());
        OpportunityStatus from = OpportunityStatus.fromCode(o.getStatus());
        OpportunityStatus to = OpportunityStatus.fromCode(dto.getTargetStatus());
        if (to == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "未知状态: " + dto.getTargetStatus());
        }
        if (from == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "商机当前状态非法: " + o.getStatus());
        }
        if (!from.canTransitTo(to)) {
            throw new BizException(BizErrorCode.BAD_REQUEST,
                    "状态不允许迁移: " + from.getDesc() + " → " + to.getDesc());
        }
        if (to == OpportunityStatus.LOST && !StringUtils.hasText(dto.getLostReason())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "输单原因不能为空");
        }
        opportunityMapper.updateStatus(o.getId(), to.getCode(), dto.getLostReason());
        log.info("[Opportunity] 状态迁移: id={} {} -> {}", o.getId(), from.getCode(), to.getCode());
    }

    @Override
    public void delete(Long id) {
        OpportunityDO o = getById(id);
        opportunityMapper.deleteById(o.getId());
        log.info("[Opportunity] 删除商机: id={}", id);
    }

    @Override
    public OpportunityDO getById(Long id) {
        OpportunityDO o = opportunityMapper.selectById(id);
        if (o == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "商机不存在");
        }
        return o;
    }

    @Override
    public Page<OpportunityDO> page(int page, int size, String keyword, String status, String level, Long ownerId) {
        Page<OpportunityDO> p = new Page<>(page, size);
        LambdaQueryWrapper<OpportunityDO> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            w.and(qw -> qw.like(OpportunityDO::getOpportunityCode, keyword)
                    .or().like(OpportunityDO::getOpportunityName, keyword)
                    .or().like(OpportunityDO::getCustomerName, keyword));
        }
        if (StringUtils.hasText(status)) w.eq(OpportunityDO::getStatus, status);
        if (StringUtils.hasText(level)) w.eq(OpportunityDO::getLevel, level);
        if (ownerId != null) w.eq(OpportunityDO::getOwnerId, ownerId);
        w.orderByDesc(OpportunityDO::getCreatedAt);
        return opportunityMapper.selectPage(p, w);
    }

    @Override
    public BigDecimal evaluateWinRate(Long id, String customerCredit, boolean hasHistory) {
        OpportunityDO o = getById(id);
        BigDecimal rate = WinRateEvaluator.evaluate(o, customerCredit, hasHistory);
        o.setWinRate(rate);
        opportunityMapper.updateById(o);
        return rate;
    }

    @Override
    public List<Map<String, Object>> aggregateByStatus(Long tenantId) {
        if (tenantId == null) tenantId = 1L;
        return opportunityMapper.aggregateByStatus(tenantId);
    }

    @Override
    public List<Map<String, Object>> aggregateByLevel(Long tenantId) {
        if (tenantId == null) tenantId = 1L;
        return opportunityMapper.aggregateByLevel(tenantId);
    }

    private void validate(OpportunityCreateDTO dto) {
        if (dto == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "请求不能为空");
        }
        if (!StringUtils.hasText(dto.getOpportunityCode())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "商机编号不能为空");
        }
        if (!StringUtils.hasText(dto.getOpportunityName())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "商机名称不能为空");
        }
        if (dto.getCustomerId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "客户 ID 不能为空");
        }
        if (dto.getOwnerId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "负责人 ID 不能为空");
        }
    }

    private String safeCustomerName(Long id) {
        try { return nameAssembler == null ? null : nameAssembler.resolveCustomer(id); }
        catch (Exception e) { return null; }
    }

    private String safeEmployeeName(Long id) {
        try { return nameAssembler == null ? null : nameAssembler.resolveEmployee(id); }
        catch (Exception e) { return null; }
    }
}
