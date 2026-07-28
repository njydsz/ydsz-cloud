package com.njydsz.project.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.project.domain.dto.post.ProjectInitiationPostDTO;
import com.njydsz.project.domain.dto.put.ProjectInitiationPutDTO;
import com.njydsz.project.domain.dto.ProjectInitiationPageQuery;
import com.njydsz.project.domain.vo.ProjectInitiationVO;
import com.njydsz.project.server.service.ProjectInitiationService;

import jakarta.validation.Valid;

import java.util.List;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.vo.ProjectInitiationVO;
import com.njydsz.common.lock.annotation.Idempotent;

/**
 * 项目立项 Controller
 *
 * <p>提供项目立项环节的 REST API，是「项目管理」业务域的<b>入口</b> Controller。
 * 对标大厂 PMIS / 经营管理系统中的「项目立项 / 项目立项申请 / 项目台账」管理界面。
 *
 * <p><b>核心接口：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #page} / {@link #save} / {@link #update} /
 *       {@link #remove}</li>
 *   <li><b>业务专用</b>：{@link #getByCode}（按项目编号查）/
 *       {@link #advanceStage}（推进项目阶段）/
 *       {@link #listByPmId}（按 PM 查项目）</li>
 * </ul>
 *
 * <p><b>项目状态机：</b>
 * <pre>
 *  PRE_INITIATION → INITIATION → CONTRACT → EXECUTION → CLOSURE
 *       (预立项)       (立项)     (合同)     (执行)     (收尾)
 * </pre>
 *
 * <p><b>安全控制：</b>
 * <ul>
 *   <li>所有写接口 {@code @Audit} 审计，写操作落 {@code ydsz_operation_log}</li>
 *   <li>分页查询受 {@code DataScopeInterceptor} 数据权限控制，PM 仅可见自己负责的项目</li>
 *   <li>{@code @Valid} 触发 JSR-303 校验，错误由 {@code GlobalExceptionHandler} 统一处理</li>
 * </ul>
 *
 * <p><b>典型链路：</b>
 * <ol>
 *   <li>销售创建商机 → 商机赢单后调用 {@link #save} 创建预立项</li>
 *   <li>PM 在「立项申请」页面调用 {@link #update} 完善立项信息</li>
 *   <li>PM 调用 {@link #advanceStage} 推进项目阶段（PRE_INITIATION → INITIATION）</li>
 *   <li>PM 工作台调用 {@link #listByPmId} 加载自己的项目列表</li>
 * </ol>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.project.server.service.ProjectInitiationService 立项 Service
 * @see com.njydsz.project.domain.dto.post.ProjectInitiationPostDTO 立项创建 DTO
 * @see com.njydsz.project.domain.dto.put.ProjectInitiationPutDTO 立项更新 DTO
 * @see com.njydsz.project.domain.dto.ProjectInitiationPageQuery 立项分页查询 DTO
 */
@RestController
@RequestMapping("/api/v1/project/initiation")
@RequiredArgsConstructor
public class ProjectInitiationController {

    private final ProjectInitiationService projectInitiationService;

    /**
     * 按 ID 查询项目立项详情
     *
     * @param id 项目主键 ID
     * @return 项目立项视图对象
     */
    @GetMapping("/{id}")
    public BaseResponse<ProjectInitiationVO> getById(@PathVariable String id) {
        return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(projectInitiationService.getById(id)));
    }

    /**
     * 按项目编号查询
     *
     * @param projectCode 项目编号
     * @return 项目立项视图对象
     */
    @GetMapping("/code/{projectCode}")
    public BaseResponse<ProjectInitiationVO> getByCode(@PathVariable String projectCode) {
        return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(projectInitiationService.getByCode(projectCode)));
    }

    /**
     * 分页查询项目立项列表
     *
     * @param query 分页查询条件
     * @return 分页项目立项视图对象
     */
    @GetMapping("/page")
    public PageResponse<ProjectInitiationVO> page(@Valid ProjectInitiationPageQuery query) {
        IPage<ProjectInitiationVO> result = projectInitiationService.page(query);
        return PageResponse.success(ProjectConverter.INSTANT.projectInitiationListToVO(result.getRecords()), result.getTotal(),
                (int) result.getCurrent(), (int) result.getSize());
    }

    /**
     * 创建项目立项
     *
     * @param dto 项目立项创建入参
     * @return 创建后的项目 ID
     */
    @Idempotent(key = "ydsz:project:ProjectInitiationController:save:lock", ttlSeconds = 5)
    @PostMapping
    @Audit(action = AuditAction.CREATE, module = "PROJECT", content= "创建项目立项")
    public BaseResponse<String> save(@Valid @RequestBody ProjectInitiationPostDTO dto) {
        return BaseResponse.success(projectInitiationService.save(ProjectConverter.INSTANT.postDtoToEntity(dto)));
    }

    /**
     * 更新项目立项
     *
     * @param dto 项目立项更新入参
     * @return 是否更新成功
     */
    @Idempotent(key = "ydsz:project:ProjectInitiationController:update:lock", ttlSeconds = 5)
    @PutMapping
    @Audit(action = AuditAction.UPDATE, module = "PROJECT", content= "更新项目立项")
    public BaseResponse<Boolean> update(@Valid @RequestBody ProjectInitiationPutDTO dto) {
        return BaseResponse.success(projectInitiationService.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto)));
    }

    /**
     * 按 ID 删除项目立项
     *
     * @param id 项目主键 ID
     * @return 是否删除成功
     */
    @Idempotent(key = "ydsz:project:ProjectInitiationController:remove:lock", ttlSeconds = 5)
    @DeleteMapping("/{id}")
    @Audit(action = AuditAction.DELETE, module = "PROJECT", content= "删除项目立项")
    public BaseResponse<Boolean> remove(@PathVariable String id) {
        return BaseResponse.success(projectInitiationService.removeById(id));
    }

    /**
     * 推进项目阶段
     *
     * @param id    项目主键 ID
     * @param stage 目标阶段
     * @param gate  门径评审阶段（可选）
     * @return 是否推进成功
     */
    @Idempotent(key = "ydsz:project:ProjectInitiationController:advanceStage:lock", ttlSeconds = 5)
    @PutMapping("/{id}/stage")
    @Audit(action = AuditAction.UPDATE, module = "PROJECT", content= "推进项目阶段")
    public BaseResponse<Boolean> advanceStage(@PathVariable String id,
                                               @RequestParam String stage,
                                               @RequestParam(required = false) String gate) {
        return BaseResponse.success(projectInitiationService.advanceStage(id, stage, gate));
    }

    /**
     * 按项目经理 ID 查询项目列表
     *
     * @param pmId 项目经理用户 ID
     * @return 项目立项视图对象列表
     */
    @GetMapping("/pm/{pmId}")
    public BaseResponse<List<ProjectInitiationVO>> listByPmId(@PathVariable String pmId) {
        return BaseResponse.success(ProjectConverter.INSTANT.projectInitiationListToVO(projectInitiationService.listByPmId(pmId)));
    }
}
