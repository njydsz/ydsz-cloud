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

    /** 商机跟进 Mapper */
    private final OpportunityFollowMapper followMapper;
    /** 商机 Mapper（用于校验商机是否存在） */
    private final OpportunityMapper opportunityMapper;

    /**
     * 记录一次商机跟进。
     * <p>校验商机存在性 → 属性拷贝 → 设置跟进时间 → 持久化。</p>
     *
     * @param dto 跟进记录参数
     * @return 跟进记录 ID
     * @throws BizException 商机不存在或参数非法时抛出
     */
    @Override
    public Long record(OpportunityFollowDTO dto) {
        validate(dto);
        if (opportunityMapper.selectById(dto.getOpportunityId()) == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "error.project.msg_69bdeff3");
        }
        OpportunityFollowDO f = new OpportunityFollowDO();
        BeanUtils.copyProperties(dto, f);
        f.setFollowAt(LocalDateTime.now());
        followMapper.insert(f);
        log.info("[OpportunityFollow] 记录跟进: opp={} type={}", dto.getOpportunityId(), dto.getFollowType());
        return f.getId();
    }

    /**
     * 分页查询商机跟进记录，按跟进时间倒序。
     *
     * @param page         页码（从 1 开始）
     * @param size         每页大小
     * @param opportunityId 商机 ID，可空（为空则查全部）
     * @return 分页结果
     */
    @Override
    public Page<OpportunityFollowDO> page(int page, int size, Long opportunityId) {
        Page<OpportunityFollowDO> p = new Page<>(page, size);
        LambdaQueryWrapper<OpportunityFollowDO> w = new LambdaQueryWrapper<>();
        if (opportunityId != null) w.eq(OpportunityFollowDO::getOpportunityId, opportunityId);
        w.orderByDesc(OpportunityFollowDO::getFollowAt);
        return followMapper.selectPage(p, w);
    }

    /**
     * 校验跟进记录参数，确保商机 ID 与跟进类型非空。
     *
     * @param dto 跟进记录参数
     * @throws BizException 参数非法时抛出
     */
    private void validate(OpportunityFollowDTO dto) {
        if (dto == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.project.msg_d9712a58");
        }
        if (dto.getOpportunityId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.project.msg_5cdeabc1");
        }
        if (!StringUtils.hasText(dto.getFollowType())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.project.msg_5b2e099f");
        }
    }
}
