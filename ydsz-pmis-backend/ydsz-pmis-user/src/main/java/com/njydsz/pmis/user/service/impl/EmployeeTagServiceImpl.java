package com.njydsz.pmis.user.service.impl;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.user.dto.EmployeeTagCreateDTO;
import com.njydsz.pmis.user.entity.EmployeeTagDO;
import com.njydsz.pmis.user.enums.TagType;
import com.njydsz.pmis.user.mapper.EmployeeTagMapper;
import com.njydsz.pmis.user.service.EmployeeTagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 人员标签服务实现
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeTagServiceImpl implements EmployeeTagService {

    private final EmployeeTagMapper tagMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long add(EmployeeTagCreateDTO dto) {
        validate(dto);
        EmployeeTagDO t = new EmployeeTagDO();
        BeanUtils.copyProperties(dto, t);
        if (t.getProficiency() == null) t.setProficiency(3);
        if (t.getYearsExp() == null) t.setYearsExp(0);
        if (t.getTenantId() == null) t.setTenantId(1L);
        if (t.getProviderTraceId() == null) t.setProviderTraceId("");
        tagMapper.insert(t);
        log.info("[EmpTag] 添加标签: emp={} {}={}", t.getEmployeeId(), t.getTagType(), t.getTagCode());
        return t.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        if (id == null) throw new BizException(BizErrorCode.BAD_REQUEST, "ID 不能为空");
        tagMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replaceByEmployee(Long employeeId, List<EmployeeTagCreateDTO> tags) {
        if (employeeId == null) throw new BizException(BizErrorCode.BAD_REQUEST, "员工 ID 不能为空");
        tagMapper.deleteByEmployee(employeeId);
        if (tags == null) return;
        for (EmployeeTagCreateDTO dto : tags) {
            dto.setEmployeeId(employeeId);
            add(dto);
        }
    }

    @Override
    public List<EmployeeTagDO> listByEmployee(Long employeeId) {
        if (employeeId == null) return List.of();
        return tagMapper.selectByEmployee(employeeId);
    }

    @Override
    public List<EmployeeTagDO> findCandidates(String tagType, String tagCode) {
        if (!StringUtils.hasText(tagType)) return List.of();
        return tagMapper.selectByTag(tagType, tagCode);
    }

    private void validate(EmployeeTagCreateDTO dto) {
        if (dto == null) throw new BizException(BizErrorCode.BAD_REQUEST, "请求不能为空");
        if (dto.getEmployeeId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "员工 ID 不能为空");
        }
        if (TagType.fromCode(dto.getTagType()) == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "无效标签类型: " + dto.getTagType());
        }
        if (!StringUtils.hasText(dto.getTagCode())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "标签编码不能为空");
        }
        if (!StringUtils.hasText(dto.getTagName())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "标签名称不能为空");
        }
        if (dto.getProficiency() != null && (dto.getProficiency() < 1 || dto.getProficiency() > 5)) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "熟练度必须 1-5");
        }
    }
}
