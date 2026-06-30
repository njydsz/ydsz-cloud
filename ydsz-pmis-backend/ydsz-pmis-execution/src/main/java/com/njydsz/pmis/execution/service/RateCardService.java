package com.njydsz.pmis.execution.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.execution.dto.RateCardCreateDTO;
import com.njydsz.pmis.execution.entity.RateCardDO;

import java.time.LocalDate;
import java.util.List;

/**
 * 对外报价费率服务
 */
public interface RateCardService {

    Long create(RateCardCreateDTO dto);

    void update(Long id, RateCardCreateDTO dto);

    void delete(Long id);

    RateCardDO getById(Long id);

    /** 按职级+项目类型+客户等级 命中当前生效的费率 */
    RateCardDO matchEffective(String levelCode, String projectType, String customerLevel, LocalDate date);

    List<RateCardDO> listByLevel(String levelCode);

    Page<RateCardDO> page(int page, int size, String levelCode, String status);
}
