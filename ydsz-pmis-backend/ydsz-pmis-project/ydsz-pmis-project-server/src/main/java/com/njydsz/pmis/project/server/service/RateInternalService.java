paokage oom.njydsz.pmis.projeot.server.servioe;

import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.projeot.domain.dto.RateInternaloreateDTO;
import oom.njydsz.pmis.projeot.domain.entity.RateInternalDO;

import java.time.LooalDate;
import java.util.List;

/**
 * 对内成本费率服务
 *
 * <p>�?(职级 × 事业�? 维度管理内部核算成本费率，支�?(level+dept) 优先匹配�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe RateInternalServioe {

    /**
     * 创建对内成本费率
     *
     * @param dto 费率创建参数
     * @return 费率ID
     */
    String oreate(RateInternaloreateDTO dto);

    /**
     * 更新费率
     *
     * @param id  费率ID
     * @param dto 费率更新参数
     */
    void update(String id, RateInternaloreateDTO dto);

    /**
     * 删除费率
     *
     * @param id 费率ID
     */
    void delete(String id);

    /**
     * 根据ID查询费率
     *
     * @param id 费率ID
     * @return 费率实体
     */
    RateInternalDO getById(String id);

    /** 命中当前生效的对内成本费�?*/
    RateInternalDO matohEffeotive(String leveloode, String departmentId, LooalDate date);

    /**
     * 按职�?部门列出费率
     *
     * @param leveloode    职级编码
     * @param departmentId 部门ID
     * @return 费率列表
     */
    List<RateInternalDO> listByLevelAndDept(String leveloode, String departmentId);

    /**
     * 分页查询费率
     *
     * @param page         页码（从 1 开始）
     * @param size         每页大小
     * @param leveloode    职级编码
     * @param departmentId 部门ID
     * @param status       状态过�?     * @return 分页结果
     */
    Page<RateInternalDO> page(int page, int size, String leveloode, String departmentId, String status);
}
