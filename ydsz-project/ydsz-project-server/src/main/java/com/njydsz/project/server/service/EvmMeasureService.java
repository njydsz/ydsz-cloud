package com.njydsz.project.server.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.project.domain.entity.evm.EvmMeasure;
/**
 * EVM 挣值测量 Service
 *
 * <p>管理 EVM（Earned Value Management，挣值管理）（{@code ydsz_evm_measure}）测量记录。</p>
 * <p>EVM 是项目管理的金标准度量方法，通过 PV（计划值）/ EV（挣值）/ AC（实际成本）</p>
 * <p>三个核心指标计算 SPI（进度绩效指数）/ CPI（成本绩效指数），量化项目是否按时/是否超支。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD：getById / page / save / updateById / removeById</b></li>
 *   <li><b>测量采集：定期（按周/按月）采集项目 EVM 指标</b></li>
 *   <li><b>趋势分析：连续测量形成趋势图</b></li>
 *   <li><b>异常预警：CPI<0.8 或 SPI<0.8 触发预警</b></li>
 * </ul>
 *
 * <p><b>核心指标：</b>PV（计划值） / EV（挣值） / AC（实际成本）。
 * <p><b>绩效指数：</b>SPI = EV/PV（>1 提前, <1 滞后）/ CPI = EV/AC（>1 节省, <1 超支）。
 *
 * <p><b>事务：</b>所有写操作开启 {@code @Transactional(rollbackFor = Exception.class)}。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.project.domain.entity.evm.EvmMeasure EVM 测量实体
 * @see AlertDispatchService 告警分发 Service(EVM 异常触发告警)
 * @see ProjectInitiationService 立项 Service(按项目维度汇总)
 */
public interface EvmMeasureService {
    EvmMeasure getById(String id);
    IPage<EvmMeasure> page(int pageNum, int pageSize);
    boolean save(EvmMeasure entity);
    boolean updateById(EvmMeasure entity);
    boolean removeById(String id);
}
