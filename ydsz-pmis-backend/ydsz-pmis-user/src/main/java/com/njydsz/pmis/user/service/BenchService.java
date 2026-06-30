package com.njydsz.pmis.user.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.user.dto.BenchRecordCreateDTO;
import com.njydsz.pmis.user.entity.BenchRecordDO;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Bench 闲置池服务
 */
public interface BenchService {

    /** 入池 / 出池 业务分发 */
    Long act(BenchRecordCreateDTO dto);

    /** 自动入池：项目结束触发 */
    Long autoEnter(BenchRecordCreateDTO dto);

    /** 自动出池：被新项目分配时关闭当前 Bench */
    void autoExit(Long employeeId, Long sourceAssignment, String reasonType, java.time.LocalDate exitDate);

    BenchRecordDO getById(Long id);

    BenchRecordDO getActiveByEmployee(Long employeeId);

    /** Bench 池汇总（按池统计） */
    List<Map<String, Object>> aggregateByPool();

    /** 流动统计 */
    List<Map<String, Object>> flowByDateRange(java.time.LocalDate from, java.time.LocalDate to);

    Page<BenchRecordDO> page(int page, int size, Long poolId, String status);

    /** 累计闲置成本 */
    BigDecimal totalIdleCost();
}
