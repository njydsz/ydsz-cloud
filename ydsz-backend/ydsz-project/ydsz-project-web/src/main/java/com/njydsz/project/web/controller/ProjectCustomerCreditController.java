package com.njydsz.project.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.project.domain.entity.project.ProjectCustomerCredit;
import com.njydsz.project.server.service.ProjectCustomerCreditService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.vo.ProjectCustomerCreditVO;
import com.njydsz.project.domain.dto.put.ProjectCustomerCreditPutDTO;
import com.njydsz.project.domain.dto.post.ProjectCustomerCreditPostDTO;
import com.njydsz.common.lock.annotation.Idempotent;

/**
 * 客户授信 Controller
 *
 * <p>提供客户信用评级的 REST API，是「项目管理 / 客户信用管理」业务域的 Controller。
 * 对标大厂 PMIS / CRM 系统的「客户信用 / 客户评级 / 客户授信 / 客户风险」管理界面。
 *
 * <p><b>核心接口：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #page} / {@link #save} / {@link #update} /
 *       {@link #remove}</li>
 * </ul>
 *
 * <p><b>客户评级：</b>AAA / AA / A / BBB / BB / B / C 共 7 级。
 *
 * <p><b>授信维度：</b>基于客户评级授予不同的账期 / 信用额度 / 付款方式。
 *
 * <p><b>典型调用方：</b>定时任务（每月 1 号凌晨滚动重算客户评级）。
 *
 * <p><b>安全控制：</b>
 * <ul>
 *   <li>所有写接口 {@code @Audit} 审计</li>
 *   <li>分页查询受 {@code DataScopeInterceptor} 数据权限控制</li>
 *   <li>客户信用等级变化时联动 {@code AlertDispatch} 告警</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.project.server.service.ProjectCustomerCreditService 客户信用 Service
 * @see com.njydsz.project.domain.entity.project.ProjectCustomerCredit 客户信用实体
 */
@RestController
@RequestMapping("/api/v1/project/project/customer/credit")
@RequiredArgsConstructor
public class ProjectCustomerCreditController {

    private final ProjectCustomerCreditService service;

    /**
     * 按 ID 查询授信详情
     *
     * @param id 授信主键 ID
     * @return 授信视图对象
     */
    @GetMapping("/{id}")
    public BaseResponse<ProjectCustomerCreditVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    /**
     * 分页查询授信列表
     *
     * @param p 当前页码（默认 1）
     * @param s 每页条数（默认 10）
     * @return 分页授信视图对象
     */
    @GetMapping("/page")
    public PageResponse<ProjectCustomerCreditVO> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<ProjectCustomerCredit> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.projectCustomerCreditListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    /**
     * 创建授信
     *
     * @param dto 授信创建入参
     * @return 是否创建成功
     */
    @Idempotent(key = "ydsz:project:ProjectCustomerCreditController:save:lock", ttlSeconds = 5)
    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", content="Create ProjectCustomerCredit")
    public BaseResponse<Boolean> save(@RequestBody ProjectCustomerCreditPostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    /**
     * 更新授信
     *
     * @param dto 授信更新入参
     * @return 是否更新成功
     */
    @Idempotent(key = "ydsz:project:ProjectCustomerCreditController:update:lock", ttlSeconds = 5)
    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", content="Update ProjectCustomerCredit")
    public BaseResponse<Boolean> update(@RequestBody ProjectCustomerCreditPutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    /**
     * 按 ID 删除授信
     *
     * @param id 授信主键 ID
     * @return 是否删除成功
     */
    @Idempotent(key = "ydsz:project:ProjectCustomerCreditController:remove:lock", ttlSeconds = 5)
    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", content="Delete ProjectCustomerCredit")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}
