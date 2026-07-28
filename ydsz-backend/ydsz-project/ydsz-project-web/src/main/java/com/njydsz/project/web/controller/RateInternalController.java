package com.njydsz.project.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.project.domain.entity.rate.RateInternal;
import com.njydsz.project.server.service.RateInternalService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.vo.RateInternalVO;
import com.njydsz.project.domain.dto.post.RateInternalPostDTO;
import com.njydsz.project.domain.dto.put.RateInternalPutDTO;

/**
 * 内部费率（Internal Rate）Controller
 *
 * <p>提供项目内部成本费率的 REST API，是「项目管理 / 内部成本核算 / 利润分析」业务域的 Controller。
 * 对标大厂 PMIS / 财务系统中的「内部成本费率 / 标准人工成本 / 部门费率」管理界面。
 *
 * <p><b>与 {@link RateCardController} 的区别：</b>
 * <ul>
 *   <li><b>RateCard（对外报价）</b>：用于销售报价、合同金额计算，是「收入侧」</li>
 *   <li><b>RateInternal（内部成本）</b>：用于成本归集、利润分析，是「成本侧」</li>
 *   <li>两者差异即为「项目毛利空间」：毛利 = 报价金额 × RateCard - 投入工时 × RateInternal</li>
 * </ul>
 *
 * <p><b>费率分类：</b>
 * <ul>
 *   <li><b>BY_ROLE</b>：按岗位角色（开发 / 测试 / PM）</li>
 *   <li><b>BY_LEVEL</b>：按职级（P5 / P6 / P7 / P8）</li>
 *   <li><b>BY_DEPARTMENT</b>：按部门 / 中心</li>
 *   <li><b>BY_REGION</b>：按地域（北上广深 / 二线城市）</li>
 * </ul>
 *
 * <p><b>典型链路：</b>
 * <ol>
 *   <li>HR / 财务维护内部费率 → 调用 {@link #save} / {@link #update}</li>
 *   <li>成本归集时按工时 × 内部费率计算人工成本（{@code CostAllocationService}）</li>
 *   <li>利润快照按 (RateCard - RateInternal) × 工时 计算毛利（{@code ProjectProfitSnapshotService}）</li>
 * </ol>
 *
 * <p><b>关键约束：</b>
 * <ul>
 *   <li>内部费率 <b>严禁</b> 直接删除；如需调整，应<b>新增</b>新版本而非修改历史</li>
 *   <li>已结项目（{@code status=CLOSED}）引用的费率 <b>必须</b> 保留作审计</li>
 * </ul>
 *
 * <p><b>权限控制：</b>
 * <ul>
 *   <li>查询：HR / 财务 / PMO 可见</li>
 *   <li>维护：HR + 财务双签</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see RateCardController 对外报价费率 Controller
 */
@RestController
@RequestMapping("/api/v1/project/rate/internal")
@RequiredArgsConstructor
public class RateInternalController {

    private final RateInternalService service;

    /**
     * 按 ID 查询内部费率
     *
     * <p>返回内部费率实体 + 富化的岗位角色名称 / 部门名称 / 地域名称等外键字段。
     *
     * @param id 费率主键 ID
     * @return 费率视图对象
     */
    @GetMapping("/{id}")
    public BaseResponse<RateInternalVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    /**
     * 分页查询内部费率列表
     *
     * <p>支持按分类维度（角色 / 职级 / 部门 / 地域）、生效日期范围等条件筛选。
     *
     * @param p 当前页码（默认 1）
     * @param s 每页条数（默认 10）
     * @return 分页费率视图对象
     */
    @GetMapping("/page")
    public PageResponse<RateInternalVO> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<RateInternal> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.rateInternalListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    /**
     * 创建内部费率
     *
     * <p>保存后自动触发 {@code RateInternalChangeEvent} 通知成本归集 / 利润分析模块刷新缓存。
     *
     * @param dto 费率创建入参（dimension / dimensionValue / costRate / effectiveFrom 等）
     * @return 是否创建成功
     */
    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", content="Create RateInternal")
    public BaseResponse<Boolean> save(@RequestBody RateInternalPostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    /**
     * 更新内部费率
     *
     * <p>已结项目引用的费率 <b>严禁</b> 修改（保留审计链）。如需调整，应新增新版本。
     *
     * @param dto 费率更新入参
     * @return 是否更新成功
     */
    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", content="Update RateInternal")
    public BaseResponse<Boolean> update(@RequestBody RateInternalPutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    /**
     * 按 ID 删除内部费率
     *
     * <p>采用<b>逻辑删除</b>；仅允许删除「未生效 / 未被引用」的内部费率。
     * 已被项目引用的费率 <b>严禁</b> 删除。
     *
     * @param id 费率主键 ID
     * @return 是否删除成功
     */
    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", content="Delete RateInternal")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}
