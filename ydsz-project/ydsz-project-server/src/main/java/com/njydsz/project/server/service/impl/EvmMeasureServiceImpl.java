package com.njydsz.project.server.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.project.domain.entity.evm.EvmMeasure;
import com.njydsz.project.domain.repository.evm.IEvmMeasureRepository;
import com.njydsz.project.server.service.EvmMeasureService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * EVM（挣值管理）度量 Service 实现
 *
 * <p>对 {@link EvmMeasureService} 接口的完整实现，是「项目管理 / 挣值管理」业务域的核心业务逻辑层。
 * 维护 {@code ydsz_evm_measure} 挣值度量表，
 * 对标大厂 PMIS / 项目管理系统的「EVM（Earned Value Management）/ 挣值分析 / 绩效度量」管理能力。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #page} / {@link #save} / {@link #updateById} /
 *       {@link #removeById}</li>
 *   <li><b>EVM 三大基础值</b>：维护 {@code PV}（计划值 Planned Value）/
 *       {@code EV}（挣值 Earned Value）/ {@code AC}（实际成本 Actual Cost）三大基础度量</li>
 *   <li><b>绩效指标计算</b>：基于三大基础值计算 {@code SV / CV / SPI / CPI} 等绩效指标，
 *       反映项目的进度偏差和成本偏差</li>
 *   <li><b>趋势预测</b>：基于历史 EVM 数据预测 {@code EAC}（完工估算）/ {@code ETC}（完工尚需）/
 *       {@code VAC}（完工偏差），支撑 PM 决策</li>
 * </ul>
 *
 * <p><b>事务边界：</b>
 * <ul>
 *   <li>所有写方法 {@code @Transactional(rollbackFor = Exception.class)}</li>
 *   <li>批量计算 EVM 时建议按项目分批事务提交，避免大事务长锁</li>
 * </ul>
 *
 * <p><b>多租户：</b>所有方法自动按当前 {@code TenantContext} 隔离，
 * 租户过滤由 MyBatis 拦截器注入。
 *
 * <p><b>设计要点：</b>
 * <ul>
 *   <li><b>数据来源</b>：由 {@code ydsz-job-cronjob} 定时任务（每周一凌晨）从
 *       {@code ydsz_execution_wbs_task}（进度）+ {@code ydsz_cost_allocation}（成本）聚合生成</li>
 *   <li><b>度量频率</b>：项目执行期通常按周度量，重要项目按日度量</li>
 *   <li><b>预警规则</b>：{@code CPI < 0.9} 或 {@code SPI < 0.9} 触发项目风险预警</li>
 *   <li><b>软删除</b>：采用<b>逻辑删除</b>（{@code deleted} 字段），
 *       EVM 度量是项目复盘和审计的依据，<b>严禁</b>物理删除</li>
 * </ul>
 *
 * <p><b>典型使用：</b>
 * <pre>{@code
 * // 1. 周度量：计算 EVM 三大基础值
 * EvmMeasure measure = new EvmMeasure();
 * measure.setInitiationId("project_123");
 * measure.setMeasureDate(LocalDate.now());
 * measure.setPv(new BigDecimal("1000000"));     // 计划值
 * measure.setEv(new BigDecimal("900000"));      // 挣值
 * measure.setAc(new BigDecimal("950000"));      // 实际成本
 * measure.setSv(new BigDecimal("-100000"));     // 进度偏差
 * measure.setCv(new BigDecimal("-50000"));      // 成本偏差
 * measure.setSpi(new BigDecimal("0.9"));        // 进度绩效
 * measure.setCpi(new BigDecimal("0.95"));       // 成本绩效
 * evmMeasureService.save(measure);
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see EvmMeasureService EVM 度量 Service 接口
 * @see com.njydsz.project.domain.entity.evm.EvmMeasure EVM 度量实体
 * @see com.njydsz.project.server.service.impl.ExecutionWbsTaskServiceImpl WBS 任务（PV 来源）
 * @see com.njydsz.project.server.service.impl.CostAllocationServiceImpl 成本归集（AC 来源）
 */
@Service
@RequiredArgsConstructor
public class EvmMeasureServiceImpl implements EvmMeasureService {

    /** EVM 度量仓储（聚合 Mapper + 缓存 + 事件） */
    private final IEvmMeasureRepository repository;

    /**
     * 根据主键查询 EVM 度量
     *
     * @param id EVM 度量主键
     * @return EVM 度量实体，不存在返回 null
     */
    @Override
    public EvmMeasure getById(String id) {
        return repository.getById(id);
    }

    /**
     * 分页查询 EVM 度量
     *
     * <p>通用分页接口，调用方需通过 {@code LambdaQueryWrapper} 传入业务过滤条件（如 {@code initiationId}、
     * {@code measureDate} 范围等）。
     *
     * @param pageNum  页码（1-based）
     * @param pageSize 每页条数
     * @return 分页结果（含总条数）
     */
    @Override
    public IPage<EvmMeasure> page(int pageNum, int pageSize) {
        return repository.page(new Page<>(pageNum, pageSize));
    }

    /**
     * 新增 EVM 度量
     *
     * <p><b>典型调用方：</b>定时任务（每周一凌晨滚动计算上周 EVM）。
     *
     * @param measure EVM 度量实体（不需携带 ID）
     * @return true=保存成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(EvmMeasure measure) {
        return repository.save(measure);
    }

    /**
     * 更新 EVM 度量
     *
     * <p><b>注意：</b>已发布（{@code status=PUBLISHED}）的 EVM 度量<b>严禁</b>修改，
     * 错误应通过「重算」流程纠正。
     *
     * @param measure EVM 度量实体（需携带 ID）
     * @return true=更新成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(EvmMeasure measure) {
        return repository.updateById(measure);
    }

    /**
     * 逻辑删除 EVM 度量
     *
     * <p>采用<b>逻辑删除</b>（{@code deleted=1}），不真正从 DB 删除。
     *
     * <p><b>注意：</b>EVM 度量是项目复盘和审计的依据，<b>严禁</b>物理删除。
     *
     * @param id EVM 度量主键
     * @return true=删除成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) {
        return repository.removeById(id);
    }
}
