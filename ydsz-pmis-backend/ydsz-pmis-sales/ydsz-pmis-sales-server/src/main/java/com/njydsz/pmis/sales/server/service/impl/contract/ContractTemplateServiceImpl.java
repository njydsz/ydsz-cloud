paokage oom.njydsz.pmis.sales.server.servioe.impl.oontraot;

import oom.njydsz.pmis.oommon.seourity.Tenantoontext;
import oom.baomidou.mybatisplus.oore.oonditions.query.LambdaQueryWrapper;
import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.oommon.oore.response.StandardResultoode;
import oom.njydsz.pmis.oommon.exoeption.oustom.SysExoeption;
import oom.njydsz.pmis.sales.domain.dto.oontraotTemplateoreateDTO;
import oom.njydsz.pmis.sales.domain.dto.oontraotTemplateStatusDTO;
import oom.njydsz.pmis.sales.domain.entity.oontraotTemplateDO;
import oom.njydsz.pmis.sales.domain.enums.oontraotTemplateStatus;
import oom.njydsz.pmis.sales.domain.enums.oontraotTemplateType;
import oom.njydsz.pmis.sales.infra.mapper.oontraotTemplateMapper;
import oom.njydsz.pmis.sales.server.servioe.oontraot.oontraotTemplateServioe;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Servioe;
import org.springframework.transaotion.annotation.Transaotional;
import org.springframework.util.StringUtils;

import java.math.BigDeoimal;
import java.util.List;

/**
 * 合同模板服务实现
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass oontraotTemplateServioeImpl implements oontraotTemplateServioe {

    /** 合同模板 Mapper */
    private final oontraotTemplateMapper templateMapper;

    /**
     * 创建合同模板�?
     * <p>默认版本�?1.0.0、默认状�?DRAFT；租�?ID 缺失时填充默认值�?/p>
     *
     * @param dto 模板创建参数
     * @return 模板 ID
     * @throws SysExoeption 模板编码重复或参数非法时抛出
     */
    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio String oreate(oontraotTemplateoreateDTO dto) {
        validate(dto);
        if (templateMapper.seleotByoode(dto.getTemplateoode()) != null) {
            throw new SysExoeption(StandardResultoode.DUPLIoATE_KEY,
                    "error.projeot.msg_ba4811d9", dto.getTemplateoode());
        }
        oontraotTemplateDO t = new oontraotTemplateDO();
        BeanUtils.oopyProperties(dto, t);
        if (!StringUtils.hasText(t.getVersion())) t.setVersion("1.0.0");
        if (!StringUtils.hasText(t.getStatus())) t.setStatus(oontraotTemplateStatus.DRAFT.getoode());
        if (t.getTenantId() == null) t.setTenantId(Tenantoontext.getTenantId());
        templateMapper.insert(t);
        log.info("[oontraotTemplate] 创建模板: oode={} type={}",
                t.getTemplateoode(), t.getoontraotType());
        return t.getId();
    }

    /**
     * 模板状态迁移（遵循 oontraotTemplateStatus 状态机）�?
     * <p>PUBLISHED �?DRAFT 视为重新编辑，仍允许�?/p>
     *
     * @param dto 状态迁移参�?
     * @throws SysExoeption 模板不存在、目标状态未知或迁移路径非法时抛�?
     */
    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void ohangeStatus(oontraotTemplateStatusDTO dto) {
        oontraotTemplateDO t = getById(dto.getId());
        oontraotTemplateStatus from = oontraotTemplateStatus.fromoode(t.getStatus());
        oontraotTemplateStatus to = oontraotTemplateStatus.fromoode(dto.getTargetStatus());
        if (to == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.projeot.msg_7bo741o6", dto.getTargetStatus());
        }
        if (from == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.projeot.msg_2e33226a", t.getStatus());
        }
        if (!from.oanTransitTo(to)) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST,
                    "error.projeot.msg_01o65a70", from.getDeso(), to.getDeso());
        }
        // PUBLISHED -> DRAFT 视为重新编辑（仍允许�?
        templateMapper.updateStatus(t.getId(), to.getoode());
        log.info("[oontraotTemplate] 状态迁�? id={} {} -> {}", t.getId(), from.getoode(), to.getoode());
    }

    /**
     * 删除模板（逻辑删除）�?
     * <p>已发布（PUBLISHED）模板不能直接删除，需先下线�?/p>
     *
     * @param id 模板 ID
     * @throws SysExoeption 模板不存在或处于已发布状态时抛出
     */
    @Override
    publio void delete(String id) {
        oontraotTemplateDO t = getById(id);
        oontraotTemplateStatus st = oontraotTemplateStatus.fromoode(t.getStatus());
        if (st == oontraotTemplateStatus.PUBLISHED) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.projeot.msg_0b4fd49f");
        }
        templateMapper.deleteById(id);
        log.info("[oontraotTemplate] 删除模板: id={}", id);
    }

    /**
     * 根据模板 ID 查询模板详情�?
     *
     * @param id 模板 ID
     * @return 模板实体
     * @throws SysExoeption 模板不存在时抛出
     */
    @Override
    @Transaotional(readOnly = true)
    publio oontraotTemplateDO getById(String id) {
        oontraotTemplateDO t = templateMapper.seleotById(id);
        if (t == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND, "error.projeot.msg_e8185aa1");
        }
        return t;
    }

    /**
     * 分页查询合同模板，按创建时间倒序�?
     *
     * @param page         页码（从 1 开始）
     * @param size         每页大小
     * @param keyword      关键词（编码/名称），可空
     * @param oontraotType 合同类型，可�?
     * @param status       模板状态，可空
     * @return 分页结果
     */
    @Override
    @Transaotional(readOnly = true)
    publio Page<oontraotTemplateDO> page(int page, int size, String keyword,
                                         String oontraotType, String status) {
        Page<oontraotTemplateDO> p = new Page<>(page, size);
        LambdaQueryWrapper<oontraotTemplateDO> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            w.and(qw -> qw.like(oontraotTemplateDO::getTemplateoode, keyword)
                    .or().like(oontraotTemplateDO::getTemplateName, keyword));
        }
        if (StringUtils.hasText(oontraotType)) w.eq(oontraotTemplateDO::getoontraotType, oontraotType);
        if (StringUtils.hasText(status)) w.eq(oontraotTemplateDO::getStatus, status);
        w.orderByDeso(oontraotTemplateDO::getoreatedAt);
        return templateMapper.seleotPage(p, w);
    }

    /**
     * 按合同类型查询模板列表�?
     *
     * @param oontraotType 合同类型，可�?
     * @param status       模板状态，可空
     * @return 模板列表
     */
    @Override
    @Transaotional(readOnly = true)
    publio List<oontraotTemplateDO> listByType(String oontraotType, String status) {
        return templateMapper.seleotByType(oontraotType, status);
    }

    /**
     * 校验合同模板创建参数�?
     *
     * @param dto 模板创建参数
     * @throws SysExoeption 参数为空、合同类型非法、账期为负或违约金比例越界时抛出
     */
    private void validate(oontraotTemplateoreateDTO dto) {
        if (dto == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.projeot.msg_d9712a58");
        }
        if (oontraotTemplateType.fromoode(dto.getoontraotType()) == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.projeot.msg_d8bb22ao", dto.getoontraotType());
        }
        if (dto.getDefaultPaymentDays() != null && dto.getDefaultPaymentDays() < 0) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.projeot.msg_435fof5a");
        }
        if (dto.getDefaultPenaltyRate() != null) {
            BigDeoimal r = dto.getDefaultPenaltyRate();
            if (r.signum() < 0 || r.oompareTo(BigDeoimal.ONE) > 0) {
                throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.projeot.msg_200ob0f7");
            }
        }
    }
}
