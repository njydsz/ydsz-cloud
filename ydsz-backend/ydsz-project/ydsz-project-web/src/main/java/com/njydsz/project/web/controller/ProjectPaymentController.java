package com.njydsz.project.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.project.domain.entity.project.ProjectPayment;
import com.njydsz.project.server.service.ProjectPaymentService;

import java.util.List;
import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.vo.ProjectPaymentVO;
import com.njydsz.project.domain.dto.post.ProjectPaymentPostDTO;
import com.njydsz.project.domain.dto.put.ProjectPaymentPutDTO;
import com.njydsz.common.lock.annotation.Idempotent;

/**
 * 项目回款 Controller
 *
 * <p>提供项目客户回款的 REST API，是「项目管理 / 收入回款 / 应收账款」业务域的核心 Controller。
 * 对标大厂 PMIS / 财务共享中心 / 应收管理系统（AR）中的「客户回款 / 到账登记 / 回款核销 / 银行对账」管理界面。
 *
 * <p><b>回款类型：</b>
 * <ul>
 *   <li><b>预收款</b>：合同签订后 / 服务启动前预先收取的款项</li>
 *   <li><b>进度款</b>：按项目里程碑 / WBS 阶段节点收取</li>
 *   <li><b>验收款</b>：项目验收后收取的尾款</li>
 *   <li><b>质保金</b>：合同保留金，质保期满后释放</li>
 *   <li><b>其他回款</b>：罚款扣款、补充协议款等</li>
 * </ul>
 *
 * <p><b>业务流程：</b>
 * <pre>
 *  客户付款 → 财务登记 → 银行流水匹配 → 回款核销 → AR 更新 → 利润快照增量
 *      ↑          ↑             ↑              ↑          ↑           ↑
 *   银行通知   .save()    .reconcile()    .allocate()  AR Aging   利润分摊
 * </pre>
 *
 * <p><b>关键约束：</b>
 * <ul>
 *   <li>回款金额 <b>必须</b> 等于银行到账金额（精确到分），不允许手工抹零</li>
 *   <li>回款 <b>必须</b> 关联具体合同 / 收款计划，否则视为「未识别款」进入待处理池</li>
 *   <li>已核销回款 <b>严禁</b> 直接修改金额，必须走「红冲」流程</li>
 *   <li>支持银行流水自动对账（{@code BankReconcileJob}，每日执行）</li>
 * </ul>
 *
 * <p><b>权限控制：</b>
 * <ul>
 *   <li>查询：项目成员 / 财务 / 销售总监 可见</li>
 *   <li>登记：财务独占</li>
 *   <li>核销：财务 + 销售经理 双签</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/project/project/payment")
@RequiredArgsConstructor
public class ProjectPaymentController {

    private final ProjectPaymentService service;

    /**
     * 按 ID 查询回款详情
     *
     * <p>返回回款实体 + 富化的项目名称 / 客户名称 / 合同编号 / 收款人 / 银行流水等外键字段。
     *
     * @param id 回款主键 ID
     * @return 回款视图对象
     */
    @GetMapping("/{id}")
    public BaseResponse<ProjectPaymentVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    /**
     * 分页查询回款列表
     *
     * <p>支持按项目、客户、回款类型、回款日期范围、是否核销等条件筛选。
     *
     * @param p 当前页码（默认 1）
     * @param s 每页条数（默认 10）
     * @return 分页回款视图对象
     */
    @GetMapping("/page")
    public PageResponse<List<ProjectPaymentVO>> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<ProjectPayment> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.projectPaymentListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    /**
     * 创建回款
     *
     * <p>保存后自动：
     * <ol>
     *   <li>校验金额 ≤ 收款计划余额</li>
     *   <li>触发 {@code PaymentReceivedEvent} 领域事件，联动合同收款计划核销</li>
     *   <li>更新 AR 账龄（{@code AgingSnapshotService}）</li>
     *   <li>增量更新项目利润快照（{@code ProjectProfitSnapshot}）</li>
     * </ol>
     *
     * @param dto 回款创建入参（项目 ID、合同 ID、金额、到账日期、银行流水号等）
     * @return 是否创建成功
     */
    @Idempotent(key = "ydsz:project:ProjectPaymentController:save:lock", ttlSeconds = 5)
    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", content="Create ProjectPayment")
    public BaseResponse<Boolean> save(@RequestBody ProjectPaymentPostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    /**
     * 更新回款
     *
     * <p>仅允许更新「未核销」状态的回款；已核销的回款 <b>严禁</b> 修改（由 Service 层校验）。
     * 错款处理：{@code PaymentService.reversePayment(id, reason)} 走「红冲」流程。
     *
     * @param dto 回款更新入参
     * @return 是否更新成功
     */
    @Idempotent(key = "ydsz:project:ProjectPaymentController:update:lock", ttlSeconds = 5)
    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", content="Update ProjectPayment")
    public BaseResponse<Boolean> update(@RequestBody ProjectPaymentPutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    /**
     * 按 ID 删除回款
     *
     * <p>采用<b>逻辑删除</b>；仅允许删除「未核销」状态的回款；已核销的回款 <b>严禁</b> 删除。
     * 删除后自动释放已占用的合同收款计划（{@code ContractService.releaseReceivable}）。
     *
     * @param id 回款主键 ID
     * @return 是否删除成功
     */
    @Idempotent(key = "ydsz:project:ProjectPaymentController:remove:lock", ttlSeconds = 5)
    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", content="Delete ProjectPayment")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}
