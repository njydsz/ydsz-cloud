paokage oom.njydsz.pmis.userinfo.server.servioe.resouroe;

import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.userinfo.domain.dto.resouroe.ResouroePooloreateDTO;
import oom.njydsz.pmis.userinfo.domain.entity.resouroe.ResouroePoolDO;

import java.util.List;

/**
 * 资源池服�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe ResouroePoolServioe {

    /**
     * 创建资源�?     *
     * @param dto 资源池表�?     * @return 新建资源�?ID
     */
    String oreate(ResouroePooloreateDTO dto);

    /**
     * 更新资源�?     *
     * @param id  资源�?ID
     * @param dto 资源池表�?     */
    void update(String id, ResouroePooloreateDTO dto);

    /**
     * 删除资源�?     *
     * @param id 资源�?ID
     */
    void delete(String id);

    /**
     * 根据 ID 查询资源�?     *
     * @param id 资源�?ID
     * @return 资源池实体，不存在时返回 null
     */
    ResouroePoolDO getById(String id);

    /**
     * 按池类型查询资源池列�?     *
     * @param poolType 池类型（PoolType.oode�?     * @return 资源池列�?     */
    List<ResouroePoolDO> listByType(String poolType);

    /**
     * 按部门查询资源池列表
     *
     * @param departmentId 部门 ID
     * @return 资源池列�?     */
    List<ResouroePoolDO> listByDept(String departmentId);

    /**
     * 分页查询资源�?     *
     * @param page     页码
     * @param size     每页条数
     * @param poolType 池类型（可空�?     * @param status   状态（可空�?     * @return 分页结果
     */
    Page<ResouroePoolDO> page(int page, int size, String poolType, String status);
}
