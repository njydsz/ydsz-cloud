package com.njydsz.pmis.project.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.project.dto.OpportunityFollowDTO;
import com.njydsz.pmis.project.entity.OpportunityFollowDO;
import com.njydsz.pmis.project.mapper.OpportunityFollowMapper;
import com.njydsz.pmis.project.mapper.OpportunityMapper;
import com.njydsz.pmis.project.service.OpportunityFollowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 商机跟进服务实现
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpportunityFollowServiceImpl implements OpportunityFollowService {

    private final OpportunityFollowMapper followMapper;
    private final OpportunityMapper opportunityMapper;

    @Override
    public Long record(OpportunityFollowDTO dto) {
        validate(dto);
        if (opportunityMapper.selectById(dto.getOpportunityId()) == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "商机不存在");
        }
        OpportunityFollowDO f = new OpportunityFollowDO();
        BeanUtils.copyProperties(dto, f);
        f.setFollowAt(LocalDateTime.now());
        followMapper.insert(f);
        log.info("[OpportunityFollow] 记录跟进: opp={} type={}", dto.getOpportunityId(), dto.getFollowType());
        return f.getId();
    }

    @Override
    public Page<OpportunityFollowDO> page(int page, int size, Long opportunityId) {
        Page<OpportunityFollowDO> p = new Page<>(page, size);
        LambdaQueryWrapper<OpportunityFollowDO> w = new LambdaQueryWrapper<>();
        if (opportunityId != null) w.eq(OpportunityFollowDO::getOpportunityId, opportunityId);
        w.orderByDesc(OpportunityFollowDO::getFollowAt);
        return followMapper.selectPage(p, w);
    }

    private void validate(OpportunityFollowDTO dto) {
        if (dto == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "请求不能为空");
        }
        if (dto.getOpportunityId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "商机 ID 不能为空");
        }
        if (!StringUtils.hasText(dto.getFollowType())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "跟进类型不能为空");
        }
    }
}
