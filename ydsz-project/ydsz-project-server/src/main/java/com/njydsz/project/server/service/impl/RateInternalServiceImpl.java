package com.njydsz.project.server.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.project.domain.entity.rate.RateInternal;
import com.njydsz.project.domain.repository.rate.IRateInternalRepository;
import com.njydsz.project.server.service.RateInternalService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 内部人力成本费率 Service 实现
 *
 * <p>对 {@link RateInternalService} 接口的完整实现，是「项目管理 / 内部费率 / 成本核算」业务域的核心业务逻辑层。
 * 维护 {@code ydsz_rate_internal} 内部人力成本费率表，
 * 对标大厂 PMIS / ERP 系统的「内部人力费率 / 角色成本 / 人力成本核算」管理能力。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #page} / {@link #save} / {@link #updateById} /
 *       {@link #removeById}</li>
 *   <li><b>内部费率</b>：按角色 / 级别维护公司内部人力成本费率（人月 / 人天 / 人时）</li>
 *   <li><b>成本归集</b>：工时 × 内部费率 = 人力成本，联动 {@code ydsz_cost_allocation}</li>
 *   <li><b>费率分级</b>：按岗位级别（初级 / 中级 / 高级 / 专家 / 资深专家）设定费率</li>
 *   <li><b>历史版本</b>：维护费率历史版本，支撑历史成本回算</li>
 * </ul>
 *
 * <p><b>事务边界：</b>
 * <ul>
 *   <li>所有写方法 {@code @Transactional(rollbackFor = Exception.class)}</li>
 *   <li>费率变更后历史成本<b>不</b>回算，保留历史时点的费率</li>
 * </ul>
 *
 * <p><b>多租户：</b>所有方法自动按当前 {@code TenantContext} 隔离，
 * 租户过滤由 MyBatis 拦截器注入。
 *
 * <p><b>设计要点：</b>
 * <ul>
 *   <li><b>不可变历史</b>：费率一旦生效不可修改，保留生效时点的费率快照，
 *       历史成本回算以原费率为准</li>
 *   <li><b>多维定价</b>：支持按角色（DEV / QA / PM / ARCH）/ 级别（P3-P9）/ 地区（BJ / SH / SZ）定价</li>
 *   <li><b>成本中心</b>：通过 {@code costCenter} 区分不同部门 / 成本中心的人力成本</li>
 *   <li><b>软删除</b>：采用<b>逻辑删除</b>（{@code deleted} 字段），
 *       费率记录是财务核算的法定依据，<b>严禁</b>物理删除</li>
 * </ul>
 *
 * <p><b>典型使用：</b>
 * <pre>{@code
 * // 1. HR 维护内部人力费率
 * RateInternal rate = new RateInternal();
 * rate.setRoleCode("DEV");
 * rate.setLevelCode("P6");
 * rate.setRegion("BJ");
 * rate.setRateAmount(new BigDecimal("35000"));
 * rate.setUnit("MAN_MONTH");
 * rate.setEffectiveDate(LocalDate.of(2026, 1, 1));
 * rate.setStatus("ACTIVE");
 * rateInternalService.save(rate);
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see RateInternalService 内部费率 Service 接口
 * @see com.njydsz.project.domain.entity.rate.RateInternal 内部费率实体
 * @see com.njydsz.project.server.service.impl.CostAllocationServiceImpl 成本归集（费率消费方）
 * @see com.njydsz.project.server.service.impl.RateCardServiceImpl 对外报价（对比基准）
 */
@Service
@RequiredArgsConstructor
public class RateInternalServiceImpl implements RateInternalService {

    /** 内部费率仓储（聚合 Mapper + 缓存 + 事件） */
    private final IRateInternalRepository repository;

    /**
     * 根据主键查询内部费率
     *
     * @param id 内部费率主键
     * @return 内部费率实体，不存在返回 null
     */
    @Override
    public RateInternal getById(String id) {
        return repository.getById(id);
    }

    /**
     * 分页查询内部费率
     *
     * <p>通用分页接口，调用方需通过 {@code LambdaQueryWrapper} 传入业务过滤条件（如 {@code roleCode}、
     * {@code levelCode}、{@code status} 等）。
     *
     * @param pageNum  页码（1-based）
     * @param pageSize 每页条数
     * @return 分页结果（含总条数）
     */
    @Override
    public IPage<RateInternal> page(int pageNum, int pageSize) {
        return repository.page(new Page<>(pageNum, pageSize));
    }

    /**
     * 新增内部费率
     *
     * <p>新增后应触发 {@code RateInternalEffectiveEvent} 领域事件，
     * 联动历史成本回算检查。
     *
     * @param rateInternal 内部费率实体（不需携带 ID）
     * @return true=保存成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(RateInternal rateInternal) {
        return repository.save(rateInternal);
    }

    /**
     * 更新内部费率
     *
     * <p><b>注意：</b>已生效的费率（{@code status=ACTIVE}）<b>严禁</b>修改关键字段（金额），
     * 调整应新增版本并切换，保留历史。
     *
     * @param rateInternal 内部费率实体（需携带 ID）
     * @return true=更新成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(RateInternal rateInternal) {
        return repository.updateById(rateInternal);
    }

    /**
     * 逻辑删除内部费率
     *
     * <p>采用<b>逻辑删除</b>（{@code deleted=1}），不真正从 DB 删除。
     *
     * <p><b>注意：</b>费率记录是财务核算的法定依据，<b>严禁</b>物理删除。
     *
     * @param id 内部费率主键
     * @return true=删除成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) {
        return repository.removeById(id);
    }
}
