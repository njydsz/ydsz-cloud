package com.njydsz.project.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.project.domain.entity.project.ProjectInvoice;
import com.njydsz.project.server.service.ProjectInvoiceService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.vo.ProjectInvoiceVO;
import com.njydsz.project.domain.dto.put.ProjectInvoicePutDTO;
import com.njydsz.project.domain.dto.post.ProjectInvoicePostDTO;

/**
 * 项目发票 Controller
 *
 * <p>提供项目发票（销项发票）的 REST API，是「项目管理 / 收入确认 / 财务对账」业务域的核心 Controller。
 * 对标大厂 PMIS / ERP（SAP / 用友 / 金蝶）系统中的「销项发票 / 开票申请 / 应收发票」管理界面。
 *
 * <p><b>发票类型：</b>
 * <ul>
 *   <li><b>增值税专用发票</b>：一般纳税人向一般纳税人开具，可抵扣进项税</li>
 *   <li><b>增值税普通发票</b>：开具给小规模纳税人或个人，不可抵扣</li>
 *   <li><b>电子发票（UKey）</b>：通过税务 UKey 在线开具的全数字化发票</li>
 *   <li><b>数电发票</b>：基于「全面数字化电子发票」的新版电子发票（2022 年起推广）</li>
 * </ul>
 *
 * <p><b>业务流程：</b>
 * <pre>
 *  开票申请 → 财务审核 → 税务系统开票 → 发票寄送 / 推送 → 回款核销 → 归档
 *      ↑          ↑             ↑              ↑             ↑          ↑
 *   .save()   .audit()     TaxIntegration   .deliver()   Payment   .archive()
 *                              .openInvoice
 * </pre>
 *
 * <p><b>关键约束：</b>
 * <ul>
 *   <li>单张发票金额 <b>不得</b> 超过对应合同 / 收款计划余额</li>
 *   <li>已开具（{@code status=ISSUED}）的发票 <b>严禁</b> 直接修改，必须走「红字发票」冲销</li>
 *   <li>数电发票 / UKey 发票调用 {@code TaxIntegrationClient} 实时对接税局</li>
 *   <li>开票后自动触发回款认领流程（与 {@code ProjectPayment} 关联）</li>
 * </ul>
 *
 * <p><b>权限控制：</b>
 * <ul>
 *   <li>查询：项目成员 / 财务 / 销售可见</li>
 *   <li>开票：财务角色独占，禁止业务人员直接开具</li>
 *   <li>作废：财务负责人 + 项目经理 双签审批</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/project/project/invoice")
@RequiredArgsConstructor
public class ProjectInvoiceController {

    private final ProjectInvoiceService service;

    /**
     * 按 ID 查询发票详情
     *
     * <p>返回发票实体 + 富化的项目名称 / 客户名称 / 开票人 / 复核人等外键字段。
     *
     * @param id 发票主键 ID
     * @return 发票视图对象
     */
    @GetMapping("/{id}")
    public BaseResponse<ProjectInvoiceVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    /**
     * 分页查询发票列表
     *
     * <p>支持按项目、客户、发票类型、开票日期范围、发票状态等条件筛选。
     *
     * @param p 当前页码（默认 1）
     * @param s 每页条数（默认 10）
     * @return 分页发票视图对象
     */
    @GetMapping("/page")
    public PageResponse<ProjectInvoiceVO> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<ProjectInvoice> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.projectInvoiceListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    /**
     * 创建发票
     *
     * <p>保存后自动：
     * <ol>
     *   <li>校验开票金额 ≤ 收款计划余额</li>
     *   <li>调用 {@code TaxIntegrationClient.openInvoice} 在税局开票</li>
     *   <li>回填发票号 / 发票代码 / 开票日期</li>
     * </ol>
     *
     * @param dto 发票创建入参（项目 ID、合同 ID、金额、税率、购方信息等）
     * @return 是否创建成功
     */
    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", content="Create ProjectInvoice")
    public BaseResponse<Boolean> save(@RequestBody ProjectInvoicePostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    /**
     * 更新发票
     *
     * <p>仅允许更新「待开票 / 开票失败」状态；已开具（{@code ISSUED}）的发票 <b>严禁</b> 修改。
     * 错票必须通过「红冲 + 重开」流程处理。
     *
     * @param dto 发票更新入参
     * @return 是否更新成功
     */
    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", content="Update ProjectInvoice")
    public BaseResponse<Boolean> update(@RequestBody ProjectInvoicePutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    /**
     * 按 ID 删除发票
     *
     * <p>采用<b>逻辑删除</b>；仅允许删除「草稿」状态的发票；已开具的发票 <b>严禁</b> 删除。
     * 错票处理：{@code InvoiceService.voidInvoice(id, reason)} 走「作废 / 红冲」流程。
     *
     * @param id 发票主键 ID
     * @return 是否删除成功
     */
    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", content="Delete ProjectInvoice")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}
