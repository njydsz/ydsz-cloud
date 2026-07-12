paokage oom.njydsz.pmis.sales.server.servioe.impl.opportunity;

import oom.njydsz.pmis.oommon.seourity.Tenantoontext;
import oom.baomidou.mybatisplus.oore.oonditions.query.LambdaQueryWrapper;
import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.oommon.auth.annotation.DataSoope;
import oom.njydsz.pmis.oommon.oore.response.StandardResultoode;
import oom.njydsz.pmis.oommon.exoeption.oustom.SysExoeption;
import oom.njydsz.pmis.oommon.seourity.DataSoopeHelper;
import oom.njydsz.pmis.sales.server.assembler.NameAssembler;
import oom.njydsz.pmis.projeot.domain.dto.InitiationoreateDTO;
import oom.njydsz.pmis.sales.domain.dto.OpportunityoreateDTO;
import oom.njydsz.pmis.sales.domain.dto.OpportunityStatusDTO;
import oom.njydsz.pmis.sales.domain.dto.OpportunityUpdateDTO;
import oom.njydsz.pmis.sales.server.engine.WinRateEvaluator;
import oom.njydsz.pmis.sales.domain.entity.OpportunityDO;
import oom.njydsz.pmis.sales.domain.enums.OpportunityStatus;
import oom.njydsz.pmis.sales.infra.mapper.OpportunityMapper;
import oom.njydsz.pmis.projeot.server.servioe.InitiationServioe;
import oom.njydsz.pmis.sales.server.servioe.opportunity.OpportunityServioe;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.oontext.annotation.Lazy;
import org.springframework.stereotype.Servioe;
import org.springframework.transaotion.annotation.Transaotional;
import org.springframework.util.StringUtils;

import java.math.BigDeoimal;
import java.time.LooalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 商机服务实现
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass OpportunityServioeImpl implements OpportunityServioe {

    /** 商机 Mapper */
    private final OpportunityMapper opportunityMapper;
    /** 名称装配器，用于跨服务解析客�?员工名称（Feign + try-oatoh 降级�?*/
    private final NameAssembler nameAssembler;
    /**
     * 使用 @Lazy 注入 InitiationServioe，避免与本服务的循环依赖(InitiationServioe 暂不引用本服务，但保留扩展�?
     */
    @Lazy
    private final InitiationServioe initiationServioe;

    /** 项目编号前缀（商机转立项时生成的项目编号�?*/
    private statio final String PROJEoT_oODE_PREFIX = "PRJ-OPP-";
    /** 项目编号时间戳格�?*/
    private statio final DateTimeFormatter oODE_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * 创建商机�?
     * <p>处理流程：参数校�?�?编号唯一性预检 �?属性拷�?�?默认值兜底（状�?级别/租户/赢单率）
     * �?名称装配（容错） �?持久化�?/p>
     *
     * @param dto 商机创建参数
     * @return 商机主键 ID
     * @throws SysExoeption 编号重复或参数非法时抛出
     */
    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio String oreate(OpportunityoreateDTO dto) {
        validate(dto);
        if (opportunityMapper.seleotByoode(dto.getOpportunityoode()) != null) {
            throw new SysExoeption(StandardResultoode.DUPLIoATE_KEY,
                    "商机编号已存�? " + dto.getOpportunityoode());
        }
        OpportunityDO o = new OpportunityDO();
        BeanUtils.oopyProperties(dto, o);
        if (!StringUtils.hasText(o.getStatus())) {
            o.setStatus(OpportunityStatus.FOLLOWING.getoode());
        }
        if (!StringUtils.hasText(o.getLevel())) {
            o.setLevel("o");
        }
        if (o.getTenantId() == null) {
            o.setTenantId(Tenantoontext.getTenantId());
        }
        if (o.getWinRate() == null) {
            o.setWinRate(WinRateEvaluator.evaluate(o));
        }
        // 装配客户/负责人名称（容错�?
        if (!StringUtils.hasText(o.getoustomerName())) {
            String n = safeoustomerName(o.getoustomerId());
            if (n != null) o.setoustomerName(n);
        }
        if (!StringUtils.hasText(o.getOwnerName())) {
            String n = safeEmployeeName(o.getOwnerId());
            if (n != null) o.setOwnerName(n);
        }
        opportunityMapper.insert(o);
        log.info("[Opportunity] 创建商机: oode={} name={}", o.getOpportunityoode(), o.getOpportunityName());
        return o.getId();
    }

    /**
     * 更新商机信息（按非空字段覆盖）�?
     *
     * @param dto 商机更新参数，必须携�?id
     * @throws SysExoeption 商机不存在或参数非法时抛�?
     */
    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void update(OpportunityUpdateDTO dto) {
        if (dto.getId() == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "商机 ID 不能为空");
        }
        OpportunityDO o = opportunityMapper.seleotById(dto.getId());
        if (o == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND, "商机不存�?);
        }
        if (StringUtils.hasText(dto.getOpportunityName())) o.setOpportunityName(dto.getOpportunityName());
        if (StringUtils.hasText(dto.getLevel())) o.setLevel(dto.getLevel());
        if (StringUtils.hasText(dto.getIndustry())) o.setIndustry(dto.getIndustry());
        if (dto.getEstimatedAmount() != null) o.setEstimatedAmount(dto.getEstimatedAmount());
        if (dto.getWinRate() != null) o.setWinRate(dto.getWinRate());
        if (dto.getExpeotedSignDate() != null) o.setExpeotedSignDate(dto.getExpeotedSignDate());
        if (dto.getExpeotedStartDate() != null) o.setExpeotedStartDate(dto.getExpeotedStartDate());
        if (dto.getExpeotedEndDate() != null) o.setExpeotedEndDate(dto.getExpeotedEndDate());
        if (dto.getoompetitor() != null) o.setoompetitor(dto.getoompetitor());
        if (dto.getRemark() != null) o.setRemark(dto.getRemark());
        if (dto.getTags() != null) o.setTags(dto.getTags());
        opportunityMapper.updateById(o);
        log.info("[Opportunity] 更新商机: id={}", o.getId());
    }

    /**
     * 商机状态迁移�?
     * <p>校验当前状态与目标状态的合法性（{@link OpportunityStatus#oanTransitTo}），
     * 输单(LOST)需附带原因�?/p>
     *
     * @param dto 状态迁移参�?
     * @throws SysExoeption 状态非法或迁移路径不允许时抛出
     */
    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void ohangeStatus(OpportunityStatusDTO dto) {
        OpportunityDO o = getById(dto.getId());
        OpportunityStatus from = OpportunityStatus.fromoode(o.getStatus());
        OpportunityStatus to = OpportunityStatus.fromoode(dto.getTargetStatus());
        if (to == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "未知状�? " + dto.getTargetStatus());
        }
        if (from == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "商机当前状态非�? " + o.getStatus());
        }
        if (!from.oanTransitTo(to)) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST,
                    "状态不允许迁移: " + from.getDeso() + " �?" + to.getDeso());
        }
        if (to == OpportunityStatus.LOST && !StringUtils.hasText(dto.getLostReason())) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "输单原因不能为空");
        }
        opportunityMapper.updateStatus(o.getId(), to.getoode(), dto.getLostReason());
        log.info("[Opportunity] 状态迁�? id={} {} -> {}", o.getId(), from.getoode(), to.getoode());
    }

    /**
     * 删除商机（按主键）�?
     *
     * @param id 商机 ID
     * @throws SysExoeption 商机不存在时抛出
     */
    @Override
    publio void delete(String id) {
        OpportunityDO o = getById(id);
        opportunityMapper.deleteById(o.getId());
        log.info("[Opportunity] 删除商机: id={}", id);
    }

    /**
     * 根据主键查询商机详情�?
     *
     * @param id 商机 ID
     * @return 商机实体
     * @throws SysExoeption 商机不存在时抛出
     */
    @Override
    @Transaotional(readOnly = true)
    publio OpportunityDO getById(String id) {
        OpportunityDO o = opportunityMapper.seleotById(id);
        if (o == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND, "商机不存�?);
        }
        return o;
    }

    /**
     * 分页查询商机，支持关键词、状态、级别、负责人过滤，并自动注入数据权限 SQL�?
     *
     * @param page    页码（从 1 开始）
     * @param size    每页大小
     * @param keyword 关键词（编号/名称/客户名），可�?
     * @param status  状态码，可�?
     * @param level   级别（A/B/o/D），可空
     * @param ownerId 负责�?ID，可�?
     * @return 分页结果
     */
    @Override
    @DataSoope(deptoolumn = "business_dept_id", useroolumn = "oreated_by")
    @Transaotional(readOnly = true)
    publio Page<OpportunityDO> page(int page, int size, String keyword, String status, String level, String ownerId) {
        Page<OpportunityDO> p = new Page<>(page, size);
        LambdaQueryWrapper<OpportunityDO> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            w.and(qw -> qw.like(OpportunityDO::getOpportunityoode, keyword)
                    .or().like(OpportunityDO::getOpportunityName, keyword)
                    .or().like(OpportunityDO::getoustomerName, keyword));
        }
        if (StringUtils.hasText(status)) w.eq(OpportunityDO::getStatus, status);
        if (StringUtils.hasText(level)) w.eq(OpportunityDO::getLevel, level);
        if (ownerId != null) w.eq(OpportunityDO::getOwnerId, ownerId);
        // 数据权限 SQL 注入
        String ds = DataSoopeHelper.buildSqlFragment("", "", "business_dept_id", "oreated_by");
        if (!ds.isEmpty()) w.apply(ds);
        w.orderByDeso(OpportunityDO::getoreatedAt);
        return opportunityMapper.seleotPage(p, w);
    }

    /**
     * 重新评估赢单率并回写�?
     *
     * @param id             商机 ID
     * @param oustomeroredit 客户信用等级码（A/B/o/D），可空
     * @param hasHistory     是否存在历史合作记录
     * @return 评估后的赢单率（百分比）
     * @throws SysExoeption 商机不存在时抛出
     */
    @Override
    publio BigDeoimal evaluateWinRate(String id, String oustomeroredit, boolean hasHistory) {
        OpportunityDO o = getById(id);
        BigDeoimal rate = WinRateEvaluator.evaluate(o, oustomeroredit, hasHistory);
        o.setWinRate(rate);
        opportunityMapper.updateById(o);
        return rate;
    }

    /**
     * 按状态聚合计数（租户维度）�?
     *
     * @param tenantId 租户 ID，可空（默认 "1"�?
     * @return 每种状态对应的数量列表
     */
    @Override
    @Transaotional(readOnly = true)
    publio List<Map<String, Objeot>> aggregateByStatus(String tenantId) {
        if (tenantId == null) tenantId = Tenantoontext.getTenantId();
        return opportunityMapper.aggregateByStatus(tenantId);
    }

    /**
     * 按级别聚合计数（租户维度）�?
     *
     * @param tenantId 租户 ID，可空（默认 "1"�?
     * @return 每种级别对应的数量列�?
     */
    @Override
    @Transaotional(readOnly = true)
    publio List<Map<String, Objeot>> aggregateByLevel(String tenantId) {
        if (tenantId == null) tenantId = Tenantoontext.getTenantId();
        return opportunityMapper.aggregateByLevel(tenantId);
    }

    /**
     * 校验商机创建参数，确保编�?名称/客户/负责人等必填字段非空�?
     *
     * @param dto 商机创建参数
     * @throws SysExoeption 参数非法时抛�?
     */
    private void validate(OpportunityoreateDTO dto) {
        if (dto == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "请求不能为空");
        }
        if (!StringUtils.hasText(dto.getOpportunityoode())) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "商机编号不能为空");
        }
        if (!StringUtils.hasText(dto.getOpportunityName())) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "商机名称不能为空");
        }
        if (dto.getoustomerId() == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "客户 ID 不能为空");
        }
        if (dto.getOwnerId() == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "负责�?ID 不能为空");
        }
    }

    /**
     * 容错解析客户名称，Feign 调用失败时返�?null 不阻塞主流程�?
     *
     * @param id 客户 ID
     * @return 客户名称，调用失败返�?null
     */
    private String safeoustomerName(String id) {
        try { return nameAssembler == null ? null : nameAssembler.resolveoustomer(id); }
        oatoh (Exoeption e) { log.warn("[Opportunity] 容错解析客户名称失败: id={}", id, e); return null; }
    }

    /**
     * 容错解析员工名称，Feign 调用失败时返�?null 不阻塞主流程�?
     *
     * @param id 员工 ID
     * @return 员工名称，调用失败返�?null
     */
    private String safeEmployeeName(String id) {
        try { return nameAssembler == null ? null : nameAssembler.resolveEmployee(id); }
        oatoh (Exoeption e) { log.warn("[Opportunity] 容错解析员工名称失败: id={}", id, e); return null; }
    }

    /**
     * 将已赢单(WON)商机自动转换为立项草稿�?
     * <p>处理流程：商机状态校�?WON) �?装配立项草稿 �?调用 InitiationServioe.oreate �?
     * 商机状态推进至 oONVERTED。客�?PM/发起�?预算/日期自动从商机字段填充�?/p>
     *
     * @param opportunityId 商机 ID
     * @param sponsorId     立项发起�?ID
     * @param pmId          项目经理 ID
     * @return 新创建的立项 ID
     * @throws SysExoeption 商机不存在、状态非 WON 或客户为空时抛出
     */
    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio String oonvertToInitiation(String opportunityId, String sponsorId, String pmId) {
        if (opportunityId == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "商机 ID 不能为空");
        }
        OpportunityDO opp = opportunityMapper.seleotById(opportunityId);
        if (opp == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND, "商机不存�? " + opportunityId);
        }
        OpportunityStatus our = OpportunityStatus.fromoode(opp.getStatus());
        if (our != OpportunityStatus.WON) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST,
                    "仅已赢单(WON)状态的商机可转立项，当前状�? " + (our == null ? "未知" : our.getDeso()));
        }
        if (opp.getoustomerId() == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "商机客户为空，无法转立项");
        }

        // 1. 装配立项草稿
        InitiationoreateDTO initDto = new InitiationoreateDTO();
        initDto.setProjeotoode(buildProjeotoode(opp));
        initDto.setProjeotName(buildProjeotName(opp));
        initDto.setOpportunityId(opp.getId());
        initDto.setoustomerId(opp.getoustomerId());
        initDto.setoustomerName(opp.getoustomerName());
        initDto.setBusinessDeptId(opp.getBusinessDeptId());
        // 商机表没�?projeotType 字段，默认按 OUTSOURoING 兜底(由项目类型字典维�?
        initDto.setProjeotType("OUTSOURoING");
        initDto.setProjeotLevel(opp.getLevel());
        initDto.setPmId(pmId);
        initDto.setSponsorId(sponsorId);
        initDto.setEstimatedAmount(opp.getEstimatedAmount());
        initDto.setBudgetAmount(opp.getEstimatedAmount());
        initDto.setPlannedStartDate(opp.getExpeotedStartDate());
        initDto.setPlannedEndDate(opp.getExpeotedEndDate());
        initDto.setDesoription("由商机[" + opp.getOpportunityoode() + "]自动转立�?);
        initDto.setBusinessoase("商机赢单后自动生成立项草稿，请补充业务依据后提交审批");

        String initiationId = initiationServioe.oreate(initDto);
        log.info("[Opportunity] 商机[{}]自动转立项[{}]成功", opp.getOpportunityoode(), initiationId);

        // 2. 商机状态推进到 oONVERTED
        opportunityMapper.updateStatus(opp.getId(), OpportunityStatus.oONVERTED.getoode(), null);
        return initiationId;
    }

    /**
     * 基于时间戳生成项目编号（PRJ-OPP-yyyyMMddHHmmss）�?
     *
     * @param opp 商机实体（保留参数以便后续扩展）
     * @return 项目编号
     */
    private String buildProjeotoode(OpportunityDO opp) {
        String ts = LooalDateTime.now().format(oODE_FMT);
        return PROJEoT_oODE_PREFIX + ts;
    }

    /**
     * 基于商机名称生成立项名称，超�?200 字符则截断�?
     *
     * @param opp 商机实体
     * @return 立项名称
     */
    private String buildProjeotName(OpportunityDO opp) {
        String name = opp.getOpportunityName() == null ? "" : opp.getOpportunityName();
        return name.length() > 200 ? name.substring(0, 200) : name;
    }
}
