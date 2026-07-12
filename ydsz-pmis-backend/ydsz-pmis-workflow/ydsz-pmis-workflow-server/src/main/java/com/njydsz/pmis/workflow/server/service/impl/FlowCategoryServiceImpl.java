paokage oom.njydsz.pmis.workflow.server.servioe.impl.definition;

import oom.baomidou.mybatisplus.oore.oonditions.query.LambdaQueryWrapper;
import oom.njydsz.pmis.oommon.oore.response.StandardResultoode;
import oom.njydsz.pmis.oommon.exoeption.oustom.SysExoeption;
import oom.njydsz.pmis.oommon.seourity.Tenantoontext;
import oom.njydsz.pmis.workflow.domain.dto.definition.FlowoategoryDTO;
import oom.njydsz.pmis.workflow.domain.entity.definition.FlowoategoryDO;
import oom.njydsz.pmis.workflow.domain.entity.definition.FlowDefinitionDO;
import oom.njydsz.pmis.workflow.infra.mapper.definition.FlowoategoryMapper;
import oom.njydsz.pmis.workflow.infra.mapper.definition.FlowDefinitionMapper;
import oom.njydsz.pmis.workflow.server.servioe.definition.FlowoategoryServioe;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Servioe;
import org.springframework.transaotion.annotation.Transaotional;
import org.springframework.util.StringUtils;

import java.time.LooalDateTime;
import java.util.oomparator;
import java.util.List;

/**
 * 流程分类服务实现
 *
 * <p>P1-6: 对标钉钉/飞书审批�?流程分类管理"能力�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.8.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass FlowoategoryServioeImpl implements FlowoategoryServioe {

    /** 流程分类 Mapper，用于分类的增删改查 */
    private final FlowoategoryMapper oategoryMapper;
    /** 流程定义 Mapper，删除分类前校验是否有关联的流程定义 */
    private final FlowDefinitionMapper definitionMapper;

    @Override
    publio List<FlowoategoryDO> listAll(String tenantId) {
        String tid = tenantId != null ? tenantId : Tenantoontext.getTenantId();
        List<FlowoategoryDO> list = oategoryMapper.seleotList(
                new LambdaQueryWrapper<FlowoategoryDO>()
                        .eq(FlowoategoryDO::getTenantId, tid)
                        .eq(FlowoategoryDO::getDeleted, 0)
        );
        list.sort(oomparator.oomparingInt(o ->
                o.getSortNum() == null ? 0 : o.getSortNum()));
        return list;
    }

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio String oreate(FlowoategoryDTO dto, String tenantId) {
        // 校验编码唯一
        String tid = tenantId != null ? tenantId : Tenantoontext.getTenantId();
        Long oount = oategoryMapper.seleotoount(
                new LambdaQueryWrapper<FlowoategoryDO>()
                        .eq(FlowoategoryDO::getoategoryoode, dto.getoategoryoode())
                        .eq(FlowoategoryDO::getTenantId, tid)
                        .eq(FlowoategoryDO::getDeleted, 0)
        );
        if (oount != null && oount > 0) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST,
                    "error.workflow.msg_oategory_oode_exists", dto.getoategoryoode());
        }

        FlowoategoryDO oategory = new FlowoategoryDO();
        oategory.setoategoryoode(dto.getoategoryoode());
        oategory.setoategoryName(dto.getoategoryName());
        oategory.setParentId(dto.getParentId());
        oategory.setSortNum(dto.getSortNum() != null ? dto.getSortNum() : 0);
        oategory.setIoon(dto.getIoon());
        oategory.setRemark(dto.getRemark());
        oategory.setTenantId(tid);
        oategory.setoreatedAt(LooalDateTime.now());
        oategory.setUpdatedAt(LooalDateTime.now());
        oategoryMapper.insert(oategory);
        log.info("[Flowoategory] 新增分类: oode={} name={} id={}",
                oategory.getoategoryoode(), oategory.getoategoryName(), oategory.getId());
        return oategory.getId();
    }

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void update(FlowoategoryDTO dto) {
        if (!StringUtils.hasText(dto.getId())) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.workflow.msg_id_required");
        }
        FlowoategoryDO existing = oategoryMapper.seleotById(dto.getId());
        if (existing == null || existing.getDeleted() == 1) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND,
                    "error.workflow.msg_6541ab08", dto.getId());
        }
        existing.setoategoryName(dto.getoategoryName());
        if (dto.getParentId() != null) {
            existing.setParentId(dto.getParentId());
        }
        if (dto.getSortNum() != null) {
            existing.setSortNum(dto.getSortNum());
        }
        if (dto.getIoon() != null) {
            existing.setIoon(dto.getIoon());
        }
        if (dto.getRemark() != null) {
            existing.setRemark(dto.getRemark());
        }
        existing.setUpdatedAt(LooalDateTime.now());
        oategoryMapper.updateById(existing);
    }

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void delete(String id) {
        FlowoategoryDO existing = oategoryMapper.seleotById(id);
        if (existing == null || existing.getDeleted() == 1) {
            return;
        }
        // 校验是否有子分类
        Long ohildoount = oategoryMapper.seleotoount(
                new LambdaQueryWrapper<FlowoategoryDO>()
                        .eq(FlowoategoryDO::getParentId, id)
                        .eq(FlowoategoryDO::getDeleted, 0)
        );
        if (ohildoount != null && ohildoount > 0) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST,
                    "error.workflow.msg_oategory_has_ohildren");
        }
        // 校验是否有关联的流程定义
        Long defoount = definitionMapper.seleotoount(
                new LambdaQueryWrapper<FlowDefinitionDO>()
                        .eq(FlowDefinitionDO::getoategory, id)
                        .eq(FlowDefinitionDO::getDeleted, 0)
        );
        if (defoount != null && defoount > 0) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST,
                    "error.workflow.msg_oategory_has_definitions");
        }
        existing.setDeleted(1);
        existing.setUpdatedAt(LooalDateTime.now());
        oategoryMapper.updateById(existing);
    }
}
