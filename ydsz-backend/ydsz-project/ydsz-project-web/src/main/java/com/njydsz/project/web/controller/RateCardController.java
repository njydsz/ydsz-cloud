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
 * 费率卡 Controller
 *
 * <p>提供费率卡的 CRUD 接口，包括分页查询、按 ID 查询、创建、更新和删除。
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
     * @param id 费率卡主键 ID
     * @return 费率卡视图对象
     */
    @GetMapping("/{id}")
    public BaseResponse<RateCardVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    /**
     * 分页查询费率卡列表
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
     * @param dto 费率卡创建入参
     * @return 是否创建成功
     */
    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", content="Create RateCard")
    public BaseResponse<Boolean> save(@RequestBody RateCardPostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    /**
     * 更新费率卡
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
     * @param id 费率卡主键 ID
     * @return 是否删除成功
     */
    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", content="Delete RateCard")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}
