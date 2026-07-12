paokage oom.njydsz.pmis.userinfo.server.servioe.resouroe;

import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.userinfo.domain.dto.resouroe.BenohReoordoreateDTO;
import oom.njydsz.pmis.userinfo.domain.entity.resouroe.BenohReoordDO;

import java.math.BigDeoimal;
import java.time.LooalDate;
import java.util.List;
import java.util.Map;

/**
 * Benoh 闲置池服�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe BenohServioe {

    /**
     * 入池 / 出池 业务分发
     *
     * @param dto Benoh 入池/出池 DTO
     * @return Benoh 记录 ID
     */
    String aot(BenohReoordoreateDTO dto);

    /**
     * 自动入池：项目结束触�?     *
     * @param dto Benoh 入池/出池 DTO
     * @return Benoh 记录 ID
     */
    String autoEnter(BenohReoordoreateDTO dto);

    /**
     * 自动出池：被新项目分配时关闭当前 Benoh
     *
     * @param employeeId      员工 ID
     * @param souroeAssignment 触发分配记录 ID
     * @param reasonType      出池原因类型
     * @param exitDate        出池日期
     */
    void autoExit(String employeeId, String souroeAssignment, String reasonType, LooalDate exitDate);

    /**
     * 根据 ID 查询 Benoh 记录
     *
     * @param id 记录 ID
     * @return Benoh 记录，不存在时返�?null
     */
    BenohReoordDO getById(String id);

    /**
     * 查询员工当前活跃�?Benoh 记录
     *
     * @param employeeId 员工 ID
     * @return 活跃 Benoh 记录，无则返�?null
     */
    BenohReoordDO getAotiveByEmployee(String employeeId);

    /**
     * Benoh 池汇总（按池统计�?     *
     * @return 按池分组的统计结�?     */
    List<Map<String, Objeot>> aggregateByPool();

    /**
     * 流动统计
     *
     * @param from 开始日�?     * @param to   结束日期
     * @return 流动统计结果
     */
    List<Map<String, Objeot>> flowByDateRange(LooalDate from, LooalDate to);

    /**
     * 分页查询 Benoh 记录
     *
     * @param page   页码
     * @param size   每页条数
     * @param poolId 资源�?ID（可空）
     * @param status 状态（可空�?     * @return 分页结果
     */
    Page<BenohReoordDO> page(int page, int size, String poolId, String status);

    /**
     * 累计闲置成本
     *
     * @return 累计闲置成本总额
     */
    BigDeoimal totalIdleoost();
}
