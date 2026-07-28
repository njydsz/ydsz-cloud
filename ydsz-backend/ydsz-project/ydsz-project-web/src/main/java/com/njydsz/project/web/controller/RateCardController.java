package com.njydsz.project.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.project.domain.entity.rate.RateCard;
import com.njydsz.project.server.service.RateCardService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.vo.RateCardVO;
import com.njydsz.project.domain.dto.put.RateCardPutDTO;
import com.njydsz.project.domain.dto.post.RateCardPostDTO;

/**
 * 费率卡（Rate Card）Controller
 *
 * <p>提供项目费率卡的 REST API，是「项目管理 / 报价管理 / 成本核算」业务域的 Controller。
 * 对标大厂 PMIS / 财务系统中的「标准费率 / 客户报价 / 人天单价 / 工时费率」管理界面。
 *
 * <p><b>费率卡类型：</b>
 * <ul>
 *   <li><b>STANDARD</b>：标准费率卡（内部默认），适用于常规项目报价</li>
 *   <li><b>PROMOTIONAL</b>：促销费率卡，适用于战略客户 / 大客户的优惠报价</li>
 *   <li><b>CONTRACT</b>：合同费率卡，绑定具体合同的固定费率</li>
 *   <li><b>INTERNAL</b>：内部成本费率卡，用于成本归集（非对外报价）</li>
 * </ul>
 *
 * <p><b>核心字段：</b>
 * <ul>
 *   <li>{@code role}：岗位角色（如 高级开发 / 架构师 / PM）</li>
 *   <li>{@code level}：职级（P5 / P6 / P7 / P8）</li>
 *   <li>{@code dailyRate}：人天单价（含税 / 不含税）</li>
 *   <li>{@code hourlyRate}：人时单价（按 8 小时 / 天折算）</li>
 *   <li>{@code effectiveFrom / effectiveTo}：生效起止日期</li>
 * </ul>
 *
 * <p><b>典型链路：</b>
 * <ol>
 *   <li>财务 / 销售管理员维护费率卡 → 调用 {@link #save} / {@link #update}</li>
 *   <li>商机报价时引用费率卡 → {@code ProjectOpportunityService.quoteByRateCard}</li>
 *   <li>合同签订后冻结费率卡 → 关联到具体合同（{@code ProjectContract.rateCardId}）</li>
 *   <li>成本归集时按费率卡计算人工成本（{@code CostAllocationService}）</li>
 * </ol>
 *
 * <p><b>关键约束：</b>
 * <ul>
 *   <li>已签订合同引用的费率卡 <b>严禁</b> 直接删除（保留审计链）</li>
 *   <li>同一 {@code role+level} 组合在生效期间内 <b>必须</b> 唯一</li>
 *   <li>费率卡变更需经财务审批（{@code @Audit} 审计 + 工作流）</li>
 * </ul>
 *
 * <p><b>权限控制：</b>
 * <ul>
 *   <li>查询：销售 / 财务 / PMO 可见</li>
 *   <li>维护：财务 / 销售管理员独占</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/project/rate/card")
@RequiredArgsConstructor
public class RateCardController {

    private final RateCardService service;

    /**
     * 按 ID 查询费率卡
     *
     * <p>返回费率卡实体 + 富化的岗位角色名称 / 职级名称等外键字段。
     *
     * @param id 费率卡主键 ID
     * @return 费率卡视图对象
     */
    @GetMapping("/{id}")
    public BaseResponse<RateCardVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    /**
     * 分页查询费率卡列表
     *
     * <p>支持按费率类型、岗位角色、职级、生效日期范围等条件筛选。
     *
     * @param p 当前页码（默认 1）
     * @param s 每页条数（默认 10）
     * @return 分页费率卡视图对象
     */
    @GetMapping("/page")
    public PageResponse<RateCardVO> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<RateCard> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.rateCardListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    /**
     * 创建费率卡
     *
     * <p>保存后自动：
     * <ol>
     *   <li>校验 {@code (role+level+effectiveFrom)} 唯一性</li>
     *   <li>触发 {@code RateCardChangeEvent} 通知报价 / 成本模块刷新本地缓存</li>
     * </ol>
     *
     * @param dto 费率卡创建入参（role / level / dailyRate / hourlyRate / effectiveFrom 等）
     * @return 是否创建成功
     */
    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", content="Create RateCard")
    public BaseResponse<Boolean> save(@RequestBody RateCardPostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    /**
     * 更新费率卡
     *
     * <p>已签订合同引用的费率卡 <b>严禁</b> 修改（由 Service 层校验，需走「合同变更」流程）。
     * 未生效 / 历史归档的费率卡可直接修改。
     *
     * @param dto 费率卡更新入参
     * @return 是否更新成功
     */
    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", content="Update RateCard")
    public BaseResponse<Boolean> update(@RequestBody RateCardPutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    /**
     * 按 ID 删除费率卡
     *
     * <p>采用<b>逻辑删除</b>；仅允许删除「未生效 / 历史归档」的费率卡；
     * 已被合同引用的费率卡 <b>严禁</b> 删除。
     *
     * @param id 费率卡主键 ID
     * @return 是否删除成功
     */
    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", content="Delete RateCard")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}
