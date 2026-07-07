package com.njydsz.pmis.project.service.impl;

import com.njydsz.pmis.common.security.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.DataScope;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.security.DataScopeHelper;
import com.njydsz.pmis.project.assembler.NameAssembler;
import com.njydsz.pmis.project.dto.InitiationCreateDTO;
import com.njydsz.pmis.project.dto.OpportunityCreateDTO;
import com.njydsz.pmis.project.dto.OpportunityStatusDTO;
import com.njydsz.pmis.project.dto.OpportunityUpdateDTO;
import com.njydsz.pmis.project.engine.WinRateEvaluator;
import com.njydsz.pmis.project.entity.OpportunityDO;
import com.njydsz.pmis.project.enums.OpportunityStatus;
import com.njydsz.pmis.project.mapper.OpportunityMapper;
import com.njydsz.pmis.project.service.InitiationService;
import com.njydsz.pmis.project.service.OpportunityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

    /** 商机 Mapper */
    private final OpportunityMapper opportunityMapper;
    /** 名称装配器，用于跨服务解析客户/员工名称（Feign + try-catch 降级） */
    private final NameAssembler nameAssembler;
    /**
     * 使用 @Lazy 注入 InitiationService，避免与本服务的循环依赖(InitiationService 暂不引用本服务，但保留扩展性)
     */
    @Lazy
    private final InitiationService initiationService;

    /** 项目编号前缀（商机转立项时生成的项目编号） */
    private static final String PROJECT_CODE_PREFIX = "PRJ-OPP-";
    /** 项目编号时间戳格式 */
    private static final DateTimeFormatter CODE_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * 创建商机。
     * <p>处理流程：参数校验 → 编号唯一性预检 → 属性拷贝 → 默认值兜底（状态/级别/租户/赢单率）
     * → 名称装配（容错） → 持久化。</p>
     *
     * @param dto 商机创建参数
     * @return 商机主键 ID
     * @throws BizException 编号重复或参数非法时抛出
     */
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
            o.setTenantId(TenantContext.getTenantId());
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

    /**
     * 更新商机信息（按非空字段覆盖）。
     *
     * @param dto 商机更新参数，必须携带 id
     * @throws BizException 商机不存在或参数非法时抛出
     */
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

    /**
     * 商机状态迁移。
     * <p>校验当前状态与目标状态的合法性（{@link OpportunityStatus#canTransitTo}），
     * 输单(LOST)需附带原因。</p>
     *
     * @param dto 状态迁移参数
     * @throws BizException 状态非法或迁移路径不允许时抛出
     */
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

    /**
     * 删除商机（按主键）。
     *
     * @param id 商机 ID
     * @throws BizException 商机不存在时抛出
     */
    @Override
    public void delete(String id) {
        OpportunityDO o = getById(id);
        opportunityMapper.deleteById(o.getId());
        log.info("[Opportunity] 删除商机: id={}", id);
    }

    /**
     * 根据主键查询商机详情。
     *
     * @param id 商机 ID
     * @return 商机实体
     * @throws BizException 商机不存在时抛出
     */
    @Override
    @Transactional(readOnly = true)
    public OpportunityDO getById(String id) {
        OpportunityDO o = opportunityMapper.selectById(id);
        if (o == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "商机不存在");
        }
        return o;
    }

    /**
     * 分页查询商机，支持关键词、状态、级别、负责人过滤，并自动注入数据权限 SQL。
     *
     * @param page    页码（从 1 开始）
     * @param size    每页大小
     * @param keyword 关键词（编号/名称/客户名），可空
     * @param status  状态码，可空
     * @param level   级别（A/B/C/D），可空
     * @param ownerId 负责人 ID，可空
     * @return 分页结果
     */
    @Override
    @DataScope(deptColumn = "business_dept_id", userColumn = "created_by")
    @Transactional(readOnly = true)
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
        // 数据权限 SQL 注入
        String ds = DataScopeHelper.buildSqlFragment("", "", "business_dept_id", "created_by");
        if (!ds.isEmpty()) w.apply(ds);
        w.orderByDesc(OpportunityDO::getCreatedAt);
        return opportunityMapper.selectPage(p, w);
    }

    /**
     * 重新评估赢单率并回写。
     *
     * @param id             商机 ID
     * @param customerCredit 客户信用等级码（A/B/C/D），可空
     * @param hasHistory     是否存在历史合作记录
     * @return 评估后的赢单率（百分比）
     * @throws BizException 商机不存在时抛出
     */
    @Override
    public BigDecimal evaluateWinRate(String id, String customerCredit, boolean hasHistory) {
        OpportunityDO o = getById(id);
        BigDecimal rate = WinRateEvaluator.evaluate(o, customerCredit, hasHistory);
        o.setWinRate(rate);
        opportunityMapper.updateById(o);
        return rate;
    }

    /**
     * 按状态聚合计数（租户维度）。
     *
     * @param tenantId 租户 ID，可空（默认 1L）
     * @return 每种状态对应的数量列表
     */
    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> aggregateByStatus(String tenantId) {
        if (tenantId == null) tenantId = 1L;
        return opportunityMapper.aggregateByStatus(tenantId);
    }

    /**
     * 按级别聚合计数（租户维度）。
     *
     * @param tenantId 租户 ID，可空（默认 1L）
     * @return 每种级别对应的数量列表
     */
    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> aggregateByLevel(String tenantId) {
        if (tenantId == null) tenantId = 1L;
        return opportunityMapper.aggregateByLevel(tenantId);
    }

    /**
     * 校验商机创建参数，确保编号/名称/客户/负责人等必填字段非空。
     *
     * @param dto 商机创建参数
     * @throws BizException 参数非法时抛出
     */
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

    /**
     * 容错解析客户名称，Feign 调用失败时返回 null 不阻塞主流程。
     *
     * @param id 客户 ID
     * @return 客户名称，调用失败返回 null
     */
    private String safeCustomerName(String id) {
        try { return nameAssembler == null ? null : nameAssembler.resolveCustomer(id); }
        catch (Exception e) { log.warn("[Opportunity] 容错解析客户名称失败: id={}", id, e); return null; }
    }

    /**
     * 容错解析员工名称，Feign 调用失败时返回 null 不阻塞主流程。
     *
     * @param id 员工 ID
     * @return 员工名称，调用失败返回 null
     */
    private String safeEmployeeName(String id) {
        try { return nameAssembler == null ? null : nameAssembler.resolveEmployee(id); }
        catch (Exception e) { log.warn("[Opportunity] 容错解析员工名称失败: id={}", id, e); return null; }
    }

    /**
     * 将已赢单(WON)商机自动转换为立项草稿。
     * <p>处理流程：商机状态校验(WON) → 装配立项草稿 → 调用 InitiationService.create →
     * 商机状态推进至 CONVERTED。客户/PM/发起人/预算/日期自动从商机字段填充。</p>
     *
     * @param opportunityId 商机 ID
     * @param sponsorId     立项发起人 ID
     * @param pmId          项目经理 ID
     * @return 新创建的立项 ID
     * @throws BizException 商机不存在、状态非 WON 或客户为空时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long convertToInitiation(String opportunityId, Long sponsorId, Long pmId) {
        if (opportunityId == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "商机 ID 不能为空");
        }
        OpportunityDO opp = opportunityMapper.selectById(opportunityId);
        if (opp == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "商机不存在: " + opportunityId);
        }
        OpportunityStatus cur = OpportunityStatus.fromCode(opp.getStatus());
        if (cur != OpportunityStatus.WON) {
            throw new BizException(BizErrorCode.BAD_REQUEST,
                    "仅已赢单(WON)状态的商机可转立项，当前状态: " + (cur == null ? "未知" : cur.getDesc()));
        }
        if (opp.getCustomerId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "商机客户为空，无法转立项");
        }

        // 1. 装配立项草稿
        InitiationCreateDTO initDto = new InitiationCreateDTO();
        initDto.setProjectCode(buildProjectCode(opp));
        initDto.setProjectName(buildProjectName(opp));
        initDto.setOpportunityId(opp.getId());
        initDto.setCustomerId(opp.getCustomerId());
        initDto.setCustomerName(opp.getCustomerName());
        initDto.setBusinessDeptId(opp.getBusinessDeptId());
        // 商机表没有 projectType 字段，默认按 OUTSOURCING 兜底(由项目类型字典维护)
        initDto.setProjectType("OUTSOURCING");
        initDto.setProjectLevel(opp.getLevel());
        initDto.setPmId(pmId);
        initDto.setSponsorId(sponsorId);
        initDto.setEstimatedAmount(opp.getEstimatedAmount());
        initDto.setBudgetAmount(opp.getEstimatedAmount());
        initDto.setPlannedStartDate(opp.getExpectedStartDate());
        initDto.setPlannedEndDate(opp.getExpectedEndDate());
        initDto.setDescription("由商机[" + opp.getOpportunityCode() + "]自动转立项");
        initDto.setBusinessCase("商机赢单后自动生成立项草稿，请补充业务依据后提交审批");

        Long initiationId = initiationService.create(initDto);
        log.info("[Opportunity] 商机[{}]自动转立项[{}]成功", opp.getOpportunityCode(), initiationId);

        // 2. 商机状态推进到 CONVERTED
        opportunityMapper.updateStatus(opp.getId(), OpportunityStatus.CONVERTED.getCode(), null);
        return initiationId;
    }

    /**
     * 基于时间戳生成项目编号（PRJ-OPP-yyyyMMddHHmmss）。
     *
     * @param opp 商机实体（保留参数以便后续扩展）
     * @return 项目编号
     */
    private String buildProjectCode(OpportunityDO opp) {
        String ts = LocalDateTime.now().format(CODE_FMT);
        return PROJECT_CODE_PREFIX + ts;
    }

    /**
     * 基于商机名称生成立项名称，超过 200 字符则截断。
     *
     * @param opp 商机实体
     * @return 立项名称
     */
    private String buildProjectName(OpportunityDO opp) {
        String name = opp.getOpportunityName() == null ? "" : opp.getOpportunityName();
        return name.length() > 200 ? name.substring(0, 200) : name;
    }
}
