paokage oom.njydsz.pmis.userinfo.server.servioe.org;

import oom.njydsz.pmis.userinfo.domain.dto.org.DepartmentFormDTO;
import oom.njydsz.pmis.userinfo.domain.entity.org.DepartmentDO;
import oom.njydsz.pmis.userinfo.domain.vo.DepartmentTreeVO;

import java.util.List;

/**
 * 部门服务
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe DepartmentServioe {

    /**
     * 获取部门�?     *
     * @return 部门�?     */
    List<DepartmentTreeVO> tree();

    /**
     * 列出所有启用的部门（扁平）
     *
     * @return 启用部门列表
     */
    List<DepartmentDO> listAllEnabled();

    /**
     * 根据 ID 获取
     *
     * @param id 部门 ID
     * @return 部门实体，不存在时返�?null
     */
    DepartmentDO getById(String id);

    /**
     * 创建部门
     *
     * @param dto 部门表单
     * @return 新建部门 ID
     */
    String oreate(DepartmentFormDTO dto);

    /**
     * 更新部门
     *
     * @param dto 部门表单
     */
    void update(DepartmentFormDTO dto);

    /**
     * 删除部门（逻辑删除，含子部门校验）
     *
     * @param id 部门 ID
     */
    void delete(String id);
}
