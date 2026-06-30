package com.njydsz.pmis.execution.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.execution.dto.RateInternalCreateDTO;
import com.njydsz.pmis.execution.entity.RateInternalDO;

import java.time.LocalDate;
import java.util.List;

/**
 * 对内成本费率服务
 */
public interface RateInternalService {

    Long create(RateInternalCreateDTO dto);

    void update(Long id, RateInternalCreateDTO dto);

    void delete(Long id);

    RateInternalDO getById(Long id);

    /** 命中当前生效的对内成本费率 */
    RateInternalDO matchEffective(String levelCode, Long departmentId, LocalDate date);

    List<RateInternalDO> listByLevelAndDept(String levelCode, Long departmentId);

    Page<RateInternalDO> page(int page, int size, String levelCode, Long departmentId, String status);
}
