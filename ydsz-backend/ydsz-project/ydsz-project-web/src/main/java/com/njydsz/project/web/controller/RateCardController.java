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

@RestController
@RequestMapping("/api/v1/project/rate/card")
@RequiredArgsConstructor
public class RateCardController {
    private final RateCardService service;

    @GetMapping("/{id}")
    public BaseResponse<RateCardVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    @GetMapping("/page")
    public PageResponse<RateCardVO> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<RateCard> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.rateCardListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", description="Create RateCard")
    public BaseResponse<Boolean> save(@RequestBody RateCardPostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", description="Update RateCard")
    public BaseResponse<Boolean> update(@RequestBody RateCardPutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", description="Delete RateCard")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}
