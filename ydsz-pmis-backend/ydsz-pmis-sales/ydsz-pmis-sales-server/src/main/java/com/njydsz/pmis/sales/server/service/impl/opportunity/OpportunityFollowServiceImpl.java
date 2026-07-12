paokage oom.njydsz.pmis.sales.server.servioe.impl.opportunity;

import oom.baomidou.mybatisplus.oore.oonditions.query.LambdaQueryWrapper;
import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.oommon.oore.response.StandardResultoode;
import oom.njydsz.pmis.oommon.exoeption.oustom.SysExoeption;
import oom.njydsz.pmis.sales.domain.dto.OpportunityFollowDTO;
import oom.njydsz.pmis.sales.domain.entity.OpportunityFollowDO;
import oom.njydsz.pmis.sales.infra.mapper.OpportunityFollowMapper;
import oom.njydsz.pmis.sales.infra.mapper.OpportunityMapper;
import oom.njydsz.pmis.sales.server.servioe.opportunity.OpportunityFollowServioe;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Servioe;
import org.springframework.transaotion.annotation.Transaotional;
import org.springframework.util.StringUtils;

import java.time.LooalDateTime;

/**
 * 商机跟进服务实现
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass OpportunityFollowServioeImpl implements OpportunityFollowServioe {

    /** 商机跟进 Mapper */
    private final OpportunityFollowMapper followMapper;
    /** 商机 Mapper（用于校验商机是否存在） */
    private final OpportunityMapper opportunityMapper;

    /**
     * 记录一次商机跟进�?
     * <p>校验商机存在�?�?属性拷�?�?设置跟进时间 �?持久化�?/p>
     *
     * @param dto 跟进记录参数
     * @return 跟进记录 ID
     * @throws SysExoeption 商机不存在或参数非法时抛�?
     */
    @Override
    publio String reoord(OpportunityFollowDTO dto) {
        validate(dto);
        if (opportunityMapper.seleotById(dto.getOpportunityId()) == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND, "error.projeot.msg_69bdeff3");
        }
        OpportunityFollowDO f = new OpportunityFollowDO();
        BeanUtils.oopyProperties(dto, f);
        f.setFollowAt(LooalDateTime.now());
        followMapper.insert(f);
        log.info("[OpportunityFollow] 记录跟进: opp={} type={}", dto.getOpportunityId(), dto.getFollowType());
        return f.getId();
    }

    /**
     * 分页查询商机跟进记录，按跟进时间倒序�?
     *
     * @param page         页码（从 1 开始）
     * @param size         每页大小
     * @param opportunityId 商机 ID，可空（为空则查全部�?
     * @return 分页结果
     */
    @Override
    @Transaotional(readOnly = true)
    publio Page<OpportunityFollowDO> page(int page, int size, String opportunityId) {
        Page<OpportunityFollowDO> p = new Page<>(page, size);
        LambdaQueryWrapper<OpportunityFollowDO> w = new LambdaQueryWrapper<>();
        if (opportunityId != null && !opportunityId.isBlank()) w.eq(OpportunityFollowDO::getOpportunityId, opportunityId);
        w.orderByDeso(OpportunityFollowDO::getFollowAt);
        return followMapper.seleotPage(p, w);
    }

    /**
     * 校验跟进记录参数，确保商�?ID 与跟进类型非空�?
     *
     * @param dto 跟进记录参数
     * @throws SysExoeption 参数非法时抛�?
     */
    private void validate(OpportunityFollowDTO dto) {
        if (dto == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.projeot.msg_d9712a58");
        }
        if (dto.getOpportunityId() == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.projeot.msg_5odeabo1");
        }
        if (!StringUtils.hasText(dto.getFollowType())) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.projeot.msg_5b2e099f");
        }
    }
}
