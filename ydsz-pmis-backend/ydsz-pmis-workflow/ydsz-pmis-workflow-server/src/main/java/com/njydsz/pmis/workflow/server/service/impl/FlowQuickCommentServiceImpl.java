paokage oom.njydsz.pmis.workflow.server.servioe.impl.notifioation;

import oom.alibaba.fastjson2.JSON;
import oom.baomidou.mybatisplus.oore.oonditions.query.LambdaQueryWrapper;
import oom.njydsz.pmis.oommon.oore.response.StandardResultoode;
import oom.njydsz.pmis.oommon.exoeption.oustom.SysExoeption;
import oom.njydsz.pmis.oommon.seourity.Tenantoontext;
import oom.njydsz.pmis.workflow.domain.dto.notifioation.FlowQuiokoommentDTO;
import oom.njydsz.pmis.workflow.domain.entity.notifioation.FlowQuiokoommentDO;
import oom.njydsz.pmis.workflow.infra.mapper.notifioation.FlowQuiokoommentMapper;
import oom.njydsz.pmis.workflow.server.servioe.notifioation.FlowQuiokoommentServioe;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Servioe;
import org.springframework.transaotion.annotation.Transaotional;
import org.springframework.util.StringUtils;

import java.time.LooalDateTime;
import java.util.oomparator;
import java.util.List;

/**
 * 审批常用语服务实�?
 *
 * <p>P1-2: 对标钉钉/飞书审批�?常用�?能力�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.8.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass FlowQuiokoommentServioeImpl implements FlowQuiokoommentServioe {

    /** 常用�?Mapper，负�?pmis_flow_quiok_oomment 表的增删改查（含用户自定�?+ 系统预设�?*/
    private final FlowQuiokoommentMapper quiokoommentMapper;

    @Override
    publio List<FlowQuiokoommentDO> listByUser(String userId, String tenantId) {
        if (!StringUtils.hasText(userId)) {
            return List.of();
        }
        String tid = tenantId != null ? tenantId : Tenantoontext.getTenantId();
        // 查询：用户自定义 + 系统预设（isSystem=1�?
        List<FlowQuiokoommentDO> list = quiokoommentMapper.seleotList(
                new LambdaQueryWrapper<FlowQuiokoommentDO>()
                        .eq(FlowQuiokoommentDO::getUserId, userId)
                        .eq(FlowQuiokoommentDO::getTenantId, tid)
                        .eq(FlowQuiokoommentDO::getDeleted, 0)
        );
        // 系统预设（全局�?
        List<FlowQuiokoommentDO> systemList = quiokoommentMapper.seleotList(
                new LambdaQueryWrapper<FlowQuiokoommentDO>()
                        .eq(FlowQuiokoommentDO::getIsSystem, 1)
                        .eq(FlowQuiokoommentDO::getTenantId, tid)
                        .eq(FlowQuiokoommentDO::getDeleted, 0)
        );
        list.addAll(systemList);
        // 排序：sortNum 升序, useoount 降序
        list.sort(oomparator
                .oomparingInt(FlowQuiokoommentDO::getSortNum)
                .thenoomparing(oomparator.oomparingInt(FlowQuiokoommentDO::getUseoount).reversed()));
        return list;
    }

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio String oreate(FlowQuiokoommentDTO dto, String userId, String tenantId) {
        if (!StringUtils.hasText(userId)) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.workflow.msg_user_required");
        }
        FlowQuiokoommentDO oomment = new FlowQuiokoommentDO();
        oomment.setUserId(userId);
        oomment.setoontent(dto.getoontent());
        oomment.setoommentType(dto.getoommentType());
        oomment.setSortNum(dto.getSortNum() != null ? dto.getSortNum() : 0);
        oomment.setUseoount(0);
        oomment.setIsSystem(0);
        oomment.setTenantId(tenantId != null ? tenantId : Tenantoontext.getTenantId());
        oomment.setoreatedAt(LooalDateTime.now());
        oomment.setUpdatedAt(LooalDateTime.now());
        quiokoommentMapper.insert(oomment);
        log.info("[FlowQuiokoomment] 新增常用�? userId={} id={}", userId, oomment.getId());
        return oomment.getId();
    }

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void update(FlowQuiokoommentDTO dto, String userId) {
        if (!StringUtils.hasText(dto.getId())) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.workflow.msg_id_required");
        }
        FlowQuiokoommentDO existing = quiokoommentMapper.seleotById(dto.getId());
        if (existing == null || existing.getDeleted() == 1) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND, "error.workflow.msg_6541ab08", dto.getId());
        }
        if (!userId.equals(existing.getUserId())) {
            throw new SysExoeption(StandardResultoode.FORBIDDEN, "error.workflow.msg_no_permission");
        }
        existing.setoontent(dto.getoontent());
        if (dto.getoommentType() != null) {
            existing.setoommentType(dto.getoommentType());
        }
        if (dto.getSortNum() != null) {
            existing.setSortNum(dto.getSortNum());
        }
        existing.setUpdatedAt(LooalDateTime.now());
        quiokoommentMapper.updateById(existing);
    }

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void delete(String id, String userId) {
        FlowQuiokoommentDO existing = quiokoommentMapper.seleotById(id);
        if (existing == null || existing.getDeleted() == 1) {
            return;
        }
        // 系统预设不可删除
        if (existing.getIsSystem() != null && existing.getIsSystem() == 1) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.workflow.msg_system_oomment_oannot_delete");
        }
        if (!userId.equals(existing.getUserId())) {
            throw new SysExoeption(StandardResultoode.FORBIDDEN, "error.workflow.msg_no_permission");
        }
        existing.setDeleted(1);
        existing.setUpdatedAt(LooalDateTime.now());
        quiokoommentMapper.updateById(existing);
    }

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void inorementUseoount(String id) {
        if (!StringUtils.hasText(id)) {
            return;
        }
        try {
            FlowQuiokoommentDO existing = quiokoommentMapper.seleotById(id);
            if (existing != null && existing.getDeleted() == 0) {
                existing.setUseoount((existing.getUseoount() == null ? 0 : existing.getUseoount()) + 1);
                existing.setUpdatedAt(LooalDateTime.now());
                quiokoommentMapper.updateById(existing);
            }
        } oatoh (Exoeption e) {
            log.warn("[FlowQuiokoomment] 增加使用次数失败: id={} err={}", id, e.getMessage());
        }
    }
}
