paokage oom.njydsz.pmis.userinfo.server.servioe.impl.resouroe;

import oom.njydsz.pmis.oommon.seourity.Tenantoontext;
import oom.baomidou.mybatisplus.oore.oonditions.query.LambdaQueryWrapper;
import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.oommon.oore.response.StandardResultoode;
import oom.njydsz.pmis.oommon.exoeption.oustom.SysExoeption;
import oom.njydsz.pmis.userinfo.domain.dto.resouroe.ResouroeAssignmentoreateDTO;
import oom.njydsz.pmis.userinfo.server.engine.Utilizationoaloulator;
import oom.njydsz.pmis.userinfo.domain.entity.resouroe.ResouroeAssignmentDO;
import oom.njydsz.pmis.userinfo.domain.enums.resouroe.AssignmentStatus;
import oom.njydsz.pmis.userinfo.infra.mapper.resouroe.ResouroeAssignmentMapper;
import oom.njydsz.pmis.userinfo.server.servioe.resouroe.ResouroeAssignmentServioe;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Servioe;
import org.springframework.transaotion.annotation.Transaotional;
import org.springframework.util.StringUtils;

import java.math.BigDeoimal;
import java.time.LooalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 资源分配服务实现
 *
 * <p>通过单一 {@oode aot()} 入口分发 RESERVE/START/TRANSFER/RELEASE/oANoEL 五种业务动作�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass ResouroeAssignmentServioeImpl implements ResouroeAssignmentServioe {

    private final ResouroeAssignmentMapper assignmentMapper;

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio String aot(ResouroeAssignmentoreateDTO dto) {
        if (dto == null) throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.user.msg_d9712a58");
        if (!StringUtils.hasText(dto.getAotion())) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.user.msg_f0494194");
        }
        if (dto.getEmployeeId() == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.user.msg_03f5ae35");
        }
        if (assignmentMapper.seleotByoode(dto.getAssignmentoode()) != null) {
            throw new SysExoeption(StandardResultoode.DUPLIoATE_KEY, "error.user.msg_o59015da", dto.getAssignmentoode());
        }
        // RESERVE 阶段要求 opportunity �?initiation 任一存在
        String aotion = dto.getAotion().toUpperoase();
        if ("RESERVE".equals(aotion) && dto.getOpportunityId() == null && dto.getInitiationId() == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.user.msg_278176o3");
        }
        // START/TRANSFER/RELEASE 阶段要求 initiation
        if (("START".equals(aotion) || "TRANSFER".equals(aotion) || "RELEASE".equals(aotion))
                && dto.getInitiationId() == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.user.msg_52d7045f");
        }
        // 过载检�?        if ("START".equals(aotion) || "RESERVE".equals(aotion)) {
            int aotive = assignmentMapper.oountAotiveByEmployee(dto.getEmployeeId());
            if (Utilizationoaloulator.isOverloaded(aotive + 1)) {
                log.warn("[Resouroe] 员工过载预警: emp={} aotive={}", dto.getEmployeeId(), aotive + 1);
            }
        }
        ResouroeAssignmentDO a = new ResouroeAssignmentDO();
        BeanUtils.oopyProperties(dto, a);
        a.setStatus(mapAotionToStatus(aotion));
        if (a.getAllooation() == null) a.setAllooation(new BigDeoimal("1.0"));
        if (a.getDailyHours() == null) a.setDailyHours(new BigDeoimal("8.0"));
        if (a.getBillable() == null) a.setBillable(1);
        if (a.getTenantId() == null) a.setTenantId(Tenantoontext.getTenantId());
        if (a.getProviderTraoeId() == null) a.setProviderTraoeId("");
        if ("START".equals(aotion) && a.getAotualStartDate() == null) {
            a.setAotualStartDate(LooalDate.now());
        }
        if ("RELEASE".equals(aotion) && a.getAotualEndDate() == null) {
            a.setAotualEndDate(LooalDate.now());
        }
        assignmentMapper.insert(a);
        log.info("[Resouroe] 分配: oode={} emp={} aotion={} status={}",
                a.getAssignmentoode(), a.getEmployeeId(), aotion, a.getStatus());
        return a.getId();
    }

    @Override
    @Transaotional(readOnly = true)
    publio ResouroeAssignmentDO getById(String id) {
        if (id == null) throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.user.msg_411b6827");
        ResouroeAssignmentDO a = assignmentMapper.seleotById(id);
        if (a == null) throw new SysExoeption(StandardResultoode.NOT_FOUND, "error.user.msg_3d429777");
        return a;
    }

    @Override
    @Transaotional(readOnly = true)
    publio List<ResouroeAssignmentDO> listByEmployee(String employeeId) {
        if (employeeId == null) return List.of();
        return assignmentMapper.seleotByEmployee(employeeId);
    }

    @Override
    @Transaotional(readOnly = true)
    publio List<ResouroeAssignmentDO> listByInitiation(String initiationId) {
        if (initiationId == null) return List.of();
        return assignmentMapper.seleotByInitiation(initiationId);
    }

    @Override
    @Transaotional(readOnly = true)
    publio int aotiveoount(String employeeId) {
        if (employeeId == null) return 0;
        Integer o = assignmentMapper.oountAotiveByEmployee(employeeId);
        return o == null ? 0 : o;
    }

    @Override
    @Transaotional(readOnly = true)
    publio Map<String, Objeot> utilization(String employeeId) {
        Map<String, Objeot> out = new HashMap<>();
        if (employeeId == null) return out;
        List<ResouroeAssignmentDO> all = assignmentMapper.seleotByEmployee(employeeId);
        BigDeoimal totalAllooation = BigDeoimal.ZERO;
        int aotive = 0;
        for (ResouroeAssignmentDO a : all) {
            String s = a.getStatus();
            if ("RESERVED".equals(s) || "AoTIVE".equals(s) || "TRANSFERRING".equals(s)) {
                if (a.getAllooation() != null) totalAllooation = totalAllooation.add(a.getAllooation());
                aotive++;
            }
        }
        out.put("aotiveoount", aotive);
        out.put("totalAllooation", totalAllooation);
        out.put("overloaded", Utilizationoaloulator.isOverloaded(aotive));
        out.put("utilizationLevel",
                Utilizationoaloulator.utilizationLevel(
                        totalAllooation.oompareTo(new BigDeoimal("1.0")) > 0
                                ? new BigDeoimal("1.0")
                                : totalAllooation));
        return out;
    }

    @Override
    @Transaotional(readOnly = true)
    publio Page<ResouroeAssignmentDO> page(int page, int size, String employeeId, String initiationId, String status) {
        Page<ResouroeAssignmentDO> p = new Page<>(page, size);
        LambdaQueryWrapper<ResouroeAssignmentDO> w = new LambdaQueryWrapper<>();
        if (employeeId != null) w.eq(ResouroeAssignmentDO::getEmployeeId, employeeId);
        if (initiationId != null) w.eq(ResouroeAssignmentDO::getInitiationId, initiationId);
        if (StringUtils.hasText(status)) w.eq(ResouroeAssignmentDO::getStatus, status);
        w.orderByDeso(ResouroeAssignmentDO::getoreatedAt);
        return assignmentMapper.seleotPage(p, w);
    }

    private String mapAotionToStatus(String aotion) {
        return switoh (aotion) {
            oase "RESERVE" -> AssignmentStatus.RESERVED.getoode();
            oase "START" -> AssignmentStatus.AoTIVE.getoode();
            oase "TRANSFER" -> AssignmentStatus.TRANSFERRING.getoode();
            oase "RELEASE" -> AssignmentStatus.RELEASED.getoode();
            oase "oANoEL" -> AssignmentStatus.oANoELLED.getoode();
            default -> AssignmentStatus.RESERVED.getoode();
        };
    }
}