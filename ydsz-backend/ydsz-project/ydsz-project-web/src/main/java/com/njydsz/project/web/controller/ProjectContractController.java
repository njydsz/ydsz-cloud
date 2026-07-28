package com.njydsz.project.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.project.domain.entity.project.ProjectContract;
import com.njydsz.project.server.service.ProjectContractService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.vo.ProjectContractVO;
import com.njydsz.project.domain.dto.post.ProjectContractPostDTO;
import com.njydsz.project.domain.dto.put.ProjectContractPutDTO;

/**
 * 项目合同 Controller。
 *
 * <p>提供项目合同的 REST API，是「项目管理 / 合同环节」业务域的核心 Controller。
 * 对标大厂 PMIS / 法务系统中的「销售合同 / 服务合同 / 采购合同」管理界面。
 *
 * <h3>核心能力</h3>
 * <ul>
 *   <li>合同生命周期管理：起草（DRAFT）→ 评审（REVIEWING）→ 签订（SIGNED）→ 执行（EXECUTING）→ 归档（ARCHIVED）</li>
 *   <li>合同多版本：通过 {@link ProjectContractChangeController} 维护合同变更，保留完整审计链</li>
 *   <li>合同附件：通过 {@link ProjectContractSupplementController} 维护合同附件和补充协议</li>
 *   <li>合同模板：通过 {@link ProjectContractTemplateController} 维护标准化合同模板（销售 / 服务 / 采购）</li>
 *   <li>收款计划：合同签订后自动拆解为分期收款计划（{@code ReceivablePlan}）</li>
 * </ul>
 *
 * <h3>合同分类</h3>
 * <ul>
 *   <li>销售合同（SALES）：与客户签订的服务 / 产品销售合同</li>
 *   <li>采购合同（PURCHASE）：与供应商签订的外包 / 采购合同</li>
 *   <li>内部合同（INTERNAL）：跨部门 / 跨 BU 的内部结算合同</li>
 *   <li>框架合同（FRAMEWORK）：长期合作框架 + 多份子订单</li>
 * </ul>
 *
 * <h3>关键约束</h3>
 * <ul>
 *   <li>已签订合同（{@code status=SIGNED}）的关键字段（金额 / 工期 / 范围）<b>严禁</b>直接修改，
 *       必须通过 {@link ProjectContractChangeController} 走「合同变更」流程</li>
 *   <li>采用<b>逻辑删除</b>，合同一旦签订<b>严禁</b>物理删除</li>
 *   <li>合同金额 / 税率变更后自动触发「收款计划重算」与「利润预测更新」</li>
 * </ul>
 *
 * <h3>安全与稳定性</h3>
 * <ul>
 *   <li>所有写接口 {@code @Audit} 审计，写操作落 {@code ydsz_operation_log}</li>
 *   <li>分页查询受 {@code DataScopeInterceptor} 数据权限控制（销售 / 财务 / PM 各自可见范围）</li>
 *   <li>合同签订加分布式锁（{@code ydsz:project:contract:sign:lock}）防并发签订</li>
 * </ul>
 *
 * <h3>接口路径</h3>
 * <pre>
 *   GET    /api/v1/project/project/contract/{id}   - 按 ID 查询
 *   GET    /api/v1/project/project/contract/page   - 分页查询
 *   POST   /api/v1/project/project/contract        - 创建合同
 *   PUT    /api/v1/project/project/contract        - 更新合同
 *   DELETE /api/v1/project/project/contract/{id}   - 删除合同（逻辑删除）
 * </pre>
 *
 * <h3>架构位置</h3>
 * <pre>
 *   前端 (PC Web) → ydsz-gateway → ydsz-project-web (本 Controller)
 *                                          ↓
 *                              ydsz-project-server.ProjectContractService
 *                                          ↓
 *                              ydsz-project-infra.ProjectContractMapper
 *                                          ↓
 *                              ydsz_project_contract
 *                              ydsz_project_contract_change (合同变更)
 *                              ydsz_project_contract_supplement (合同附件)
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.project.server.service.ProjectContractService 合同 Service
 * @see com.njydsz.project.domain.entity.project.ProjectContract 合同实体
 * @see ProjectContractChangeController 合同变更 Controller
 * @see ProjectContractSupplementController 合同附件 Controller
 * @see ProjectContractTemplateController 合同模板 Controller
 */
@RestController
@RequestMapping("/api/v1/project/project/contract")
@RequiredArgsConstructor
public class ProjectContractController {

    private final ProjectContractService service;

    /**
     * 按 ID 查询合同详情。
     *
     * <p>返回合同实体 + 富化的客户名称 / 项目名称 / 合同负责人 / 签订人等外键字段。
     *
     * @param id 合同主键 ID
     * @return 合同视图对象
     */
    @GetMapping("/{id}")
    public BaseResponse<ProjectContractVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    /**
     * 分页查询合同列表。
     *
     * <p>支持按项目、客户、合同类型、合同状态、签订日期范围、合同金额区间等条件筛选。
     *
     * @param p 当前页码（默认 1）
     * @param s 每页条数（默认 10）
     * @return 分页合同视图对象
     */
    @GetMapping("/page")
    public PageResponse<ProjectContractVO> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<ProjectContract> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.projectContractListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    /**
     * 创建合同。
     *
     * <p>创建时自动：
     * <ol>
     *   <li>校验合同编号唯一性</li>
     *   <li>根据合同类型 / 金额 / 工期自动生成合同编号（{@code SALES-yyyyMMdd-xxxx}）</li>
     *   <li>签订后自动生成收款计划（{@code ReceivablePlan}）</li>
     * </ol>
     *
     * @param dto 合同创建入参
     * @return 是否创建成功
     */
    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", content="Create ProjectContract")
    public BaseResponse<Boolean> save(@RequestBody ProjectContractPostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    /**
     * 更新合同。
     *
     * <p>仅允许更新「草稿 / 评审中」状态的合同；已签订（{@code SIGNED}）的合同<b>严禁</b>直接修改，
     * 错改处理：{@code ContractService.createChangeOrder(contractId, changes)} 走「合同变更」流程。
     *
     * @param dto 合同更新入参
     * @return 是否更新成功
     */
    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", content="Update ProjectContract")
    public BaseResponse<Boolean> update(@RequestBody ProjectContractPutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    /**
     * 按 ID 删除合同。
     *
     * <p>采用<b>逻辑删除</b>；仅允许删除「草稿」状态的合同；已签订的合同<b>严禁</b>删除。
     * 错删处理：{@code ContractService.archive(contractId, reason)} 走「归档」流程。
     *
     * @param id 合同主键 ID
     * @return 是否删除成功
     */
    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", content="Delete ProjectContract")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}
