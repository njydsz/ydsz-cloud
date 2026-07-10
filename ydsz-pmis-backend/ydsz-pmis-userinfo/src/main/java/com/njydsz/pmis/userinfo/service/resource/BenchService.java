package com.njydsz.pmis.userinfo.service.resource;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.userinfo.dto.resource.BenchRecordCreateDTO;
import com.njydsz.pmis.userinfo.entity.resource.BenchRecordDO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Bench 闲置池服务
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface BenchService {

    /**
     * 入池 / 出池 业务分发
     *
     * @param dto Bench 入池/出池 DTO
     * @return Bench 记录 ID
     */
    String act(BenchRecordCreateDTO dto);

    /**
     * 自动入池：项目结束触发
     *
     * @param dto Bench 入池/出池 DTO
     * @return Bench 记录 ID
     */
    String autoEnter(BenchRecordCreateDTO dto);

    /**
     * 自动出池：被新项目分配时关闭当前 Bench
     *
     * @param employeeId      员工 ID
     * @param sourceAssignment 触发分配记录 ID
     * @param reasonType      出池原因类型
     * @param exitDate        出池日期
     */
    void autoExit(String employeeId, String sourceAssignment, String reasonType, LocalDate exitDate);

    /**
     * 根据 ID 查询 Bench 记录
     *
     * @param id 记录 ID
     * @return Bench 记录，不存在时返回 null
     */
    BenchRecordDO getById(String id);

    /**
     * 查询员工当前活跃的 Bench 记录
     *
     * @param employeeId 员工 ID
     * @return 活跃 Bench 记录，无则返回 null
     */
    BenchRecordDO getActiveByEmployee(String employeeId);

    /**
     * Bench 池汇总（按池统计）
     *
     * @return 按池分组的统计结果
     */
    List<Map<String, Object>> aggregateByPool();

    /**
     * 流动统计
     *
     * @param from 开始日期
     * @param to   结束日期
     * @return 流动统计结果
     */
    List<Map<String, Object>> flowByDateRange(LocalDate from, LocalDate to);

    /**
     * 分页查询 Bench 记录
     *
     * @param page   页码
     * @param size   每页条数
     * @param poolId 资源池 ID（可空）
     * @param status 状态（可空）
     * @return 分页结果
     */
    Page<BenchRecordDO> page(int page, int size, String poolId, String status);

    /**
     * 累计闲置成本
     *
     * @return 累计闲置成本总额
     */
    BigDecimal totalIdleCost();
}
