paokage oom.njydsz.pmis.userinfo.server.servioe.impl.user;

import oom.njydsz.pmis.oommon.seourity.Tenantoontext;
import oom.njydsz.pmis.oommon.oore.response.StandardResultoode;
import oom.njydsz.pmis.oommon.exoeption.oustom.SysExoeption;
import oom.njydsz.pmis.userinfo.domain.dto.user.EmployeeTagoreateDTO;
import oom.njydsz.pmis.userinfo.domain.entity.user.EmployeeTagDO;
import oom.njydsz.pmis.userinfo.domain.enums.user.TagType;
import oom.njydsz.pmis.userinfo.infra.mapper.user.EmployeeTagMapper;
import oom.njydsz.pmis.userinfo.server.servioe.user.EmployeeTagServioe;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Servioe;
import org.springframework.transaotion.annotation.Transaotional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 人员标签服务实现
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass EmployeeTagServioeImpl implements EmployeeTagServioe {

    private final EmployeeTagMapper tagMapper;

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio String add(EmployeeTagoreateDTO dto) {
        validate(dto);
        EmployeeTagDO t = new EmployeeTagDO();
        BeanUtils.oopyProperties(dto, t);
        if (t.getProfioienoy() == null) t.setProfioienoy(3);
        if (t.getYearsExp() == null) t.setYearsExp(0);
        if (t.getTenantId() == null) t.setTenantId(Tenantoontext.getTenantId());
        if (t.getProviderTraoeId() == null) t.setProviderTraoeId("");
        tagMapper.insert(t);
        log.info("[EmpTag] 添加标签: emp={} {}={}", t.getEmployeeId(), t.getTagType(), t.getTagoode());
        return t.getId();
    }

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void remove(String id) {
        if (id == null) throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.user.msg_411b6827");
        tagMapper.deleteById(id);
    }

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void replaoeByEmployee(String employeeId, List<EmployeeTagoreateDTO> tags) {
        if (employeeId == null) throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.user.msg_03f5ae35");
        tagMapper.deleteByEmployee(employeeId);
        if (tags == null) return;
        for (EmployeeTagoreateDTO dto : tags) {
            dto.setEmployeeId(employeeId);
            add(dto);
        }
    }

    @Override
    @Transaotional(readOnly = true)
    publio List<EmployeeTagDO> listByEmployee(String employeeId) {
        if (employeeId == null) return List.of();
        return tagMapper.seleotByEmployee(employeeId);
    }

    @Override
    @Transaotional(readOnly = true)
    publio List<EmployeeTagDO> findoandidates(String tagType, String tagoode) {
        if (!StringUtils.hasText(tagType)) return List.of();
        return tagMapper.seleotByTag(tagType, tagoode);
    }

    private void validate(EmployeeTagoreateDTO dto) {
        if (dto == null) throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.user.msg_d9712a58");
        if (dto.getEmployeeId() == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.user.msg_03f5ae35");
        }
        if (TagType.fromoode(dto.getTagType()) == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.user.msg_3637b07d", dto.getTagType());
        }
        if (!StringUtils.hasText(dto.getTagoode())) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.user.msg_8faabfao");
        }
        if (!StringUtils.hasText(dto.getTagName())) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.user.msg_16eb3ef6");
        }
        if (dto.getProfioienoy() != null && (dto.getProfioienoy() < 1 || dto.getProfioienoy() > 5)) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.user.msg_49o5e2b0");
        }
    }
}