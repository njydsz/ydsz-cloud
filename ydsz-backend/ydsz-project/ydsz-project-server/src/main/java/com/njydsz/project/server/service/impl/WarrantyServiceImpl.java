package com.njydsz.project.server.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.project.domain.entity.warranty.Warranty;
import com.njydsz.project.domain.repository.warranty.IWarrantyRepository;
import com.njydsz.project.server.service.WarrantyService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 质保期与维保服务 Service 实现
 *
 * <p>对 {@link WarrantyService} 接口的完整实现，是「项目管理 / 售后服务」业务域的核心业务逻辑层。
 * 维护 {@code ydsz_warranty} 质保期记录表，
 * 对标大厂 PMIS / 客服系统中的「质保期管理 / 维保服务 / 售后 SLA」管理能力。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #page} / {@link #save} / {@link #updateById} /
 *       {@link #removeById}</li>
 *   <li><b>质保期</b>：维护项目质保期起止时间 / 范围 / 服务等级</li>
 *   <li><b>维保服务</b>：质保期内的故障处理 / 巡检 / 升级支持</li>
 *   <li><b>质保金</b>：合同质保金管理（按比例 / 按时间段返还）</li>
 *   <li><b>服务到期提醒</b>：质保期到期前 30 天自动提醒销售 / 客户经理</li>
 * </ul>
 *
 * <p><b>事务边界：</b>
 * <ul>
 *   <li>所有写方法 {@code @Transactional(rollbackFor = Exception.class)}</li>
 *   <li>质保金返还需与合同收款计划在同一事务</li>
 * </ul>
 *
 * <p><b>多租户：</b>所有方法自动按当前 {@code TenantContext} 隔离，
 * 租户过滤由 MyBatis 拦截器注入。
 *
 * <p><b>设计要点：</b>
 * <ul>
 *   <li><b>质保期范围</b>：{@code scope} 区分 {@code ALL} 全包 /
 *       {@code PARTIAL} 部分（仅主体设备）/ {@code SOFTWARE_ONLY} 仅软件</li>
 *   <li><b>服务等级</b>：{@code slaLevel} 区分 7×24 / 5×8 / 仅工作时间 三档</li>
 *   <li><b>响应时间 SLA</b>：维护 P0 / P1 / P2 故障响应时间承诺</li>
 *   <li><b>软删除</b>：采用<b>逻辑删除</b>（{@code deleted} 字段），
 *       质保记录是售后服务的法定依据，<b>严禁</b>物理删除</li>
 * </ul>
 *
 * <p><b>典型使用：</b>
 * <pre>{@code
 * // 1. 项目关闭后启动质保期
 * Warranty warranty = new Warranty();
 * warranty.setInitiationId("project_123");
 * warranty.setContractId("contract_456");
 * warranty.setStartDate(LocalDate.now());
 * warranty.setEndDate(LocalDate.now().plusYears(1));
 * warranty.setScope("ALL");
 * warranty.setSlaLevel("7x24");
 * warranty.setResponseSla("P0=1h,P1=4h,P2=24h");
 * warranty.setWarrantyAmount(new BigDecimal("100000"));
 * warranty.setStatus("ACTIVE");
 * warrantyService.save(warranty);
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see WarrantyService 质保期 Service 接口
 * @see com.njydsz.project.domain.entity.warranty.Warranty 质保期实体
 * @see com.njydsz.project.server.service.impl.OpsTicketServiceImpl 运维工单（质保联动）
 * @see com.njydsz.project.server.service.impl.ExecutionClosureServiceImpl 项目收尾（质保启动）
 */
@Service
@RequiredArgsConstructor
public class WarrantyServiceImpl implements WarrantyService {

    /** 质保期仓储（聚合 Mapper + 缓存 + 事件） */
    private final IWarrantyRepository repository;

    /**
     * 根据主键查询质保期
     *
     * @param id 质保期主键
     * @return 质保期实体，不存在返回 null
     */
    @Override
    public Warranty getById(String id) {
        return repository.getById(id);
    }

    /**
     * 分页查询质保期
     *
     * <p>通用分页接口，调用方需通过 {@code LambdaQueryWrapper} 传入业务过滤条件（如 {@code initiationId}、
     * {@code status}、{@code slaLevel} 等）。
     *
     * @param pageNum  页码（1-based）
     * @param pageSize 每页条数
     * @return 分页结果（含总条数）
     */
    @Override
    public IPage<Warranty> page(int pageNum, int pageSize) {
        return repository.page(new Page<>(pageNum, pageSize));
    }

    /**
     * 新增质保期
     *
     * <p>新增后应触发 {@code WarrantyStartedEvent} 领域事件，
     * 联动质保金管理和服务到期提醒排程。
     *
     * @param warranty 质保期实体（不需携带 ID）
     * @return true=保存成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(Warranty warranty) {
        return repository.save(warranty);
    }

    /**
     * 更新质保期
     *
     * <p>典型场景：调整质保范围、续保、提前终止。
     *
     * @param warranty 质保期实体（需携带 ID）
     * @return true=更新成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(Warranty warranty) {
        return repository.updateById(warranty);
    }

    /**
     * 逻辑删除质保期
     *
     * <p>采用<b>逻辑删除</b>（{@code deleted=1}），不真正从 DB 删除。
     *
     * <p><b>注意：</b>质保记录是售后服务的法定依据，<b>严禁</b>物理删除。
     *
     * @param id 质保期主键
     * @return true=删除成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) {
        return repository.removeById(id);
    }
}
