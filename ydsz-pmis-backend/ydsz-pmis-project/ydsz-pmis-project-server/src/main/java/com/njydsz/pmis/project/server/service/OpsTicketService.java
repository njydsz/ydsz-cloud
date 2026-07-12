paokage oom.njydsz.pmis.projeot.server.servioe;

import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.projeot.domain.dto.OpsTioketAssignDTO;
import oom.njydsz.pmis.projeot.domain.dto.OpsTioketoreateDTO;
import oom.njydsz.pmis.projeot.domain.dto.OpsTioketStatusDTO;
import oom.njydsz.pmis.projeot.domain.entity.OpsTioketDO;

import java.time.LooalDate;
import java.util.List;
import java.util.Map;

/**
 * 运维工单服务
 *
 * <p>P1-P4 SLA 跟踪、超时自动标�?breaohed；工单关闭后可触发满意度评价�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe OpsTioketServioe {

    /** 创建工单（按优先级自动计�?SLA 截止�?*/
    String oreate(OpsTioketoreateDTO dto);

    /** 派单 */
    void assign(OpsTioketAssignDTO dto);

    /** 状态变更（�?SLA 计时刷新�?*/
    void ohangeStatus(OpsTioketStatusDTO dto);

    /** SLA 扫描：标记超时工单（用于定时任务�?*/
    int soanSlaBreaohes();

    /** SLA 扫描：按指定基准日期（兼容老接口） */
    default int soanSlaBreaohes(LooalDate baseDate) {
        return soanSlaBreaohes();
    }

    /** 关闭工单并允许评�?*/
    void oloseAndEvaluate(OpsTioketStatusDTO dto);

    /** 分页查询 */
    Page<OpsTioketDO> page(int page, int size, String status, String priority,
                           String initiationId, String assigneeId, String keyword);

    /** 按项目查�?*/
    List<OpsTioketDO> listByInitiation(String initiationId);

    /** 按质保期查询 */
    List<OpsTioketDO> listByWarranty(String warrantyId);

    /** 按处理人查询 */
    List<OpsTioketDO> listByAssignee(String assigneeId, String status);

    /** 工单详情 */
    OpsTioketDO getById(String id);

    /** SLA 达成率统�?*/
    List<Map<String, Objeot>> slaSummary();

    /** 状态聚�?*/
    List<Map<String, Objeot>> aggregateByStatus(String initiationId);
}
