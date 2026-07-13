package com.njydsz.pmis.project.server.service.impl;

import com.njydsz.pmis.common.security.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.auth.annotation.DataScope;
import com.njydsz.pmis.common.core.response.StandardResultCode;
import com.njydsz.pmis.common.exception.custom.SysException;
import com.njydsz.pmis.common.security.DataScopeHelper;
import com.njydsz.pmis.project.domain.dto.RiskCreateDTO;
import com.njydsz.pmis.project.domain.dto.RiskStatusDTO;
import com.njydsz.pmis.project.server.engine.RiskScoreEvaluator;
import com.njydsz.pmis.project.domain.entity.RiskDO;
import com.njydsz.pmis.project.domain.enums.RiskLevel;
import com.njydsz.pmis.project.domain.enums.RiskStatus;
import com.njydsz.pmis.project.infra.mapper.RiskMapper;
import com.njydsz.pmis.project.server.service.RiskService;
import com.njydsz.pmis.project.domain.vo.RiskVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 项目风险服务实现
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiskServiceImpl implements RiskService {

    /** 项目风险 Mapper */
    private final RiskMapper riskMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(RiskCreateDTO dto) {
        if (dto == null) throw new SysException(StandardResultCode.BAD_REQUEST, "error.execution.msg_d9712a58");
        if (!StringUtils.hasText(dto.getRiskCode())) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.execution.msg_cad9859b");
        }
        if (!StringUtils.hasText(dto.getRiskTitle())) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.execution.msg_def770be");
        }
        if (dto.getInitiationId() == null) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.execution.msg_576c2b5e");
        }
        if (dto.getOwnerId() == null) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.execution.msg_26804acb");
        }
        if (riskMapper.selectByCode(dto.getRiskCode()) != null) {
            throw new SysException(StandardResultCode.DUPLICATE_KEY, "error.execution.msg_25ba60bd", dto.getRiskCode());
        }
        RiskDO r = new RiskDO();
        BeanUtils.copyProperties(dto, r);
        // 自动评估风险等级
        RiskLevel level = RiskScoreEvaluator.evaluate(dto.getProbability(), dto.getImpact());
        r.setRiskLevel(level.getCode());
        if (!StringUtils.hasText(r.getStatus())) r.setStatus(RiskStatus.OPEN.getCode());
        if (r.getTenantId() == null) r.setTenantId(TenantContext.getTenantId());
        if (r.getProviderTraceId() == null) r.setProviderTraceId("");

        riskMapper.insert(r);
        log.info("[Risk] 登记风险: code={} title={} level={}",
                r.getRiskCode(), r.getRiskTitle(), r.getRiskLevel());
        return r.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(RiskStatusDTO dto) {
        if (dto == null || dto.getId() == null) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.execution.msg_d9712a58");
        }
        RiskDO r = loadByIdDO(dto.getId());
        RiskStatus from = RiskStatus.fromCode(r.getStatus());
        RiskStatus to = RiskStatus.fromCode(dto.getTargetStatus());
        if (to == null) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.execution.msg_7bc741c6", dto.getTargetStatus());
        }
        if (from == null || !from.canTransitTo(to)) {
            throw new SysException(StandardResultCode.BAD_REQUEST,
                    "error.execution.msg_95380062", (from == null ? "未知" : from.getDesc()), to.getDesc());
        }
        riskMapper.updateStatus(dto.getId(), to.getCode());
        if (to == RiskStatus.OCCURRED) r.setOccurredAt(LocalDateTime.now());
        if (to == RiskStatus.CLOSED) r.setClosedAt(LocalDateTime.now());
        riskMapper.updateById(r);
        log.info("[Risk] 状态迁移: id={} {} -> {}", dto.getId(), from.getCode(), to.getCode());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        RiskDO r = loadByIdDO(id);
        if (RiskStatus.fromCode(r.getStatus()) == RiskStatus.OCCURRED) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.execution.msg_0fa95df6");
        }
        riskMapper.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public RiskVO getById(String id) {
        RiskDO r = loadByIdDO(id);
        return toVo(r);
    }

    @Override
    @DataScope(userColumn = "owner_id")
    @Transactional(readOnly = true)
    public Page<RiskVO> page(int page, int size, String keyword, String status,
                             String riskLevel, String initiationId) {
        Page<RiskDO> p = new Page<>(page, size);
        LambdaQueryWrapper<RiskDO> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            w.and(qw -> qw.like(RiskDO::getRiskCode, keyword)
                    .or().like(RiskDO::getRiskTitle, keyword)
                    .or().like(RiskDO::getDescription, keyword));
        }
        if (StringUtils.hasText(status)) w.eq(RiskDO::getStatus, status);
        if (StringUtils.hasText(riskLevel)) w.eq(RiskDO::getRiskLevel, riskLevel);
        if (initiationId != null) w.eq(RiskDO::getInitiationId, initiationId);
        // 数据权限 SQL 注入
        String ds = DataScopeHelper.buildSqlFragment("", "", "dept_id", "owner_id");
        if (!ds.isEmpty()) w.apply(ds);
        w.orderByDesc(RiskDO::getCreatedAt);
        Page<RiskDO> doPage = riskMapper.selectPage(p, w);
        Page<RiskVO> voPage = new Page<>(doPage.getCurrent(), doPage.getSize(), doPage.getTotal());
        if (doPage.getRecords() != null && !doPage.getRecords().isEmpty()) {
            voPage.setRecords(doPage.getRecords().stream().map(this::toVo).toList());
        } else {
            voPage.setRecords(List.of());
        }
        return voPage;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RiskVO> listByInitiation(String initiationId) {
        if (initiationId == null) return List.of();
        List<RiskDO> list = riskMapper.selectByInitiation(initiationId);
        if (list == null || list.isEmpty()) return List.of();
        return list.stream().map(this::toVo).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> aggregateByLevel(String initiationId) {
        if (initiationId == null) return List.of();
        return riskMapper.aggregateByLevel(initiationId);
    }

    /**
     * 内部使用：根据 ID 加载 DO（保留所有字段，供 changeStatus/delete 等内部业务判断使用）
     *
     * <p>对外接口请使用 {@link #getById(String)} 返回 VO。
     *
     * @param id 风险ID
     * @return 风险 DO
     */
    private RiskDO loadByIdDO(String id) {
        RiskDO r = riskMapper.selectById(id);
        if (r == null) throw new SysException(StandardResultCode.NOT_FOUND, "error.execution.msg_eed2ed24");
        return r;
    }

    /**
     * DO → VO 转换（剥离 tenantId / providerTraceId / deleted / version 等敏感字段）
     *
     * <p>手写 setter 模式，参考 {@code UserAccountServiceImpl#toVo}。
     *
     * @param r 风险 DO
     * @return 风险 VO
     */
    private RiskVO toVo(RiskDO r) {
        if (r == null) return null;
        RiskVO v = new RiskVO();
        v.setId(r.getId());
        v.setRiskCode(r.getRiskCode());
        v.setInitiationId(r.getInitiationId());
        v.setRiskTitle(r.getRiskTitle());
        v.setRiskType(r.getRiskType());
        v.setDescription(r.getDescription());
        v.setProbability(r.getProbability());
        v.setImpact(r.getImpact());
        v.setRiskLevel(r.getRiskLevel());
        v.setMitigation(r.getMitigation());
        v.setContingency(r.getContingency());
        v.setOwnerId(r.getOwnerId());
        v.setOwnerName(r.getOwnerName());
        v.setStatus(r.getStatus());
        v.setOccurredAt(r.getOccurredAt());
        v.setClosedAt(r.getClosedAt());
        v.setCreatedAt(r.getCreatedAt());
        v.setUpdatedAt(r.getUpdatedAt());
        return v;
    }
}
