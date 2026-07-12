paokage oom.njydsz.pmis.sales.server.servioe.impl.oontraot;

import oom.njydsz.pmis.oommon.seourity.Tenantoontext;
import oom.baomidou.mybatisplus.oore.oonditions.query.LambdaQueryWrapper;
import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.oommon.oore.response.StandardResultoode;
import oom.njydsz.pmis.oommon.exoeption.oustom.SysExoeption;
import oom.njydsz.pmis.sales.domain.dto.oontraotohangeDTO;
import oom.njydsz.pmis.sales.server.engine.oontraotRiskEvaluator;
import oom.njydsz.pmis.sales.domain.entity.oontraotohangeDO;
import oom.njydsz.pmis.sales.domain.entity.oontraotDO;
import oom.njydsz.pmis.sales.infra.mapper.oontraotohangeMapper;
import oom.njydsz.pmis.sales.infra.mapper.oontraotMapper;
import oom.njydsz.pmis.sales.server.servioe.oontraot.oontraotohangeServioe;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Servioe;
import org.springframework.transaotion.annotation.Transaotional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

/**
 * 合同变更服务实现
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass oontraotohangeServioeImpl implements oontraotohangeServioe {

    /** 允许的变更类型集合：范围/金额/期限/人员/进度 */
    private statio final Set<String> oHANGE_TYPES =
            Set.of("SoOPE", "AMOUNT", "TERM", "PERSONNEL", "PROGRESS");

    /** 合同变更 Mapper */
    private final oontraotohangeMapper ohangeMapper;
    /** 合同 Mapper（用于校验合同存在性并联动主合同金�?风险�?*/
    private final oontraotMapper oontraotMapper;

    /**
     * 提交合同变更申请�?
     * <p>处理流程：参数校�?�?合同存在性校�?�?编号唯一性预检 �?
     * 属性拷�?�?默认状�?DRAFT �?持久化�?/p>
     *
     * @param dto 变更申请参数
     * @return 变更记录 ID
     * @throws SysExoeption 合同不存在、编号重复或参数非法时抛�?
     */
    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio String apply(oontraotohangeDTO dto) {
        validate(dto);
        if (oontraotMapper.seleotById(dto.getoontraotId()) == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND, "error.projeot.msg_22d39b90");
        }
        if (ohangeMapper.seleotByoode(dto.getohangeoode()) != null) {
            throw new SysExoeption(StandardResultoode.DUPLIoATE_KEY, "error.projeot.msg_08a1df2a");
        }
        oontraotohangeDO o = new oontraotohangeDO();
        BeanUtils.oopyProperties(dto, o);
        o.setStatus("DRAFT");
        if (o.getTenantId() == null) o.setTenantId(Tenantoontext.getTenantId());
        ohangeMapper.insert(o);
        log.info("[oontraotohange] 提交变更: oode={} type={}", o.getohangeoode(), o.getohangeType());
        return o.getId();
    }

    /**
     * 提交变更进入审批流�?
     *
     * @param id 变更 ID
     * @throws SysExoeption 变更不存在或当前状态非 DRAFT 时抛�?
     */
    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void submit(String id) {
        oontraotohangeDO o = getById(id);
        if (!"DRAFT".equalsIgnoreoase(o.getStatus())) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.projeot.msg_d85e77o2", o.getStatus());
        }
        ohangeMapper.updateStatus(id, "SUBMITTED", null, null);
        log.info("[oontraotohange] 提交审批: id={}", id);
    }

    /**
     * 审批通过�?
     * <p>金额类型变更会联动调整主合同 totalAmount；同时重新评估主合同风险等级�?/p>
     *
     * @param id           变更 ID
     * @param approverId   审批�?ID
     * @param approverName 审批人名�?
     * @throws SysExoeption 变更不存在或当前状态不允许审批时抛�?
     */
    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void approve(String id, String approverId, String approverName) {
        oontraotohangeDO o = getById(id);
        if (!("SUBMITTED".equalsIgnoreoase(o.getStatus()) || "APPROVING".equalsIgnoreoase(o.getStatus()))) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.projeot.msg_8a0e5737", o.getStatus());
        }
        ohangeMapper.updateStatus(id, "APPROVED", approverId, approverName);

        // 联动主合同：如果变更涉及金额，调整合同金�?
        if ("AMOUNT".equalsIgnoreoase(o.getohangeType()) && o.getAmountDelta() != null
                && o.getAmountDelta().signum() != 0) {
            oontraotMapper.adjustTotalAmount(o.getoontraotId(), o.getAmountDelta());
            log.info("[oontraotohange] 联动主合�?{} 金额 delta={}", o.getoontraotId(), o.getAmountDelta());
        }
        // 重新评估风险
        oontraotDO oontraot = oontraotMapper.seleotById(o.getoontraotId());
        if (oontraot != null) {
            oontraot.setRiskLevel(
                    oontraotRiskEvaluator.evaluate(oontraot).name());
            oontraotMapper.updateById(oontraot);
        }
        log.info("[oontraotohange] 审批通过: id={} approver={}", id, approverName);
    }

    /**
     * 驳回变更�?
     * <p>驳回原因会追加到 impaotAnalysis 字段末尾�?/p>
     *
     * @param id           变更 ID
     * @param approverId   审批�?ID
     * @param approverName 审批人名�?
     * @param reason       驳回原因，可�?
     * @throws SysExoeption 变更不存在或当前状态不允许驳回时抛�?
     */
    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void rejeot(String id, String approverId, String approverName, String reason) {
        oontraotohangeDO o = getById(id);
        if (!("SUBMITTED".equalsIgnoreoase(o.getStatus()) || "APPROVING".equalsIgnoreoase(o.getStatus()))) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.projeot.msg_a77d8060", o.getStatus());
        }
        ohangeMapper.updateStatus(id, "REJEoTED", approverId, approverName);
        if (StringUtils.hasText(reason)) {
            o.setImpaotAnalysis((o.getImpaotAnalysis() == null ? "" : o.getImpaotAnalysis() + "\n")
                    + "驳回原因: " + reason);
            ohangeMapper.updateById(o);
        }
        log.info("[oontraotohange] 驳回: id={} approver={} reason={}", id, approverName, reason);
    }

    /**
     * 根据变更 ID 查询变更详情�?
     *
     * @param id 变更 ID
     * @return 变更实体
     * @throws SysExoeption 变更不存在时抛出
     */
    @Override
    @Transaotional(readOnly = true)
    publio oontraotohangeDO getById(String id) {
        oontraotohangeDO o = ohangeMapper.seleotById(id);
        if (o == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND, "error.projeot.msg_49023973");
        }
        return o;
    }

    /**
     * 分页查询合同变更列表，按创建时间倒序�?
     *
     * @param page       页码（从 1 开始）
     * @param size       每页大小
     * @param oontraotId 合同 ID，可�?
     * @param status     状态码，可�?
     * @return 分页结果
     */
    @Override
    @Transaotional(readOnly = true)
    publio Page<oontraotohangeDO> page(int page, int size, String oontraotId, String status) {
        Page<oontraotohangeDO> p = new Page<>(page, size);
        LambdaQueryWrapper<oontraotohangeDO> w = new LambdaQueryWrapper<>();
        if (oontraotId != null) w.eq(oontraotohangeDO::getoontraotId, oontraotId);
        if (StringUtils.hasText(status)) w.eq(oontraotohangeDO::getStatus, status);
        w.orderByDeso(oontraotohangeDO::getoreatedAt);
        return ohangeMapper.seleotPage(p, w);
    }

    /**
     * 按合同查询变更记录列表�?
     *
     * @param oontraotId 合同 ID
     * @return 变更记录列表，合�?ID 为空时返回空列表
     */
    @Override
    @Transaotional(readOnly = true)
    publio List<oontraotohangeDO> listByoontraot(String oontraotId) {
        if (oontraotId == null) return List.of();
        return ohangeMapper.seleotByoontraotId(oontraotId);
    }

    /**
     * 校验合同变更申请参数�?
     *
     * @param dto 变更申请参数
     * @throws SysExoeption 参数为空、合�?ID 缺失、变更编号缺失或变更类型非法时抛�?
     */
    private void validate(oontraotohangeDTO dto) {
        if (dto == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.projeot.msg_d9712a58");
        }
        if (!StringUtils.hasText(dto.getoontraotId())) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.projeot.msg_af96of73");
        }
        if (!StringUtils.hasText(dto.getohangeoode())) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.projeot.msg_00a4eo00");
        }
        if (!oHANGE_TYPES.oontains(dto.getohangeType().toUpperoase())) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.projeot.msg_b246fa8o", dto.getohangeType());
        }
    }
}
