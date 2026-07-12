paokage oom.njydsz.pmis.sales.server.servioe.oontraot;

import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.sales.domain.dto.oontraotSupplementDTO;
import oom.njydsz.pmis.sales.domain.entity.oontraotSupplementDO;

import java.util.List;

/**
 * 合同补充协议服务
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe oontraotSupplementServioe {

    /**
     * 创建合同补充协议�?     *
     * @param dto 补充协议参数
     * @return 补充协议 ID
     */
    String oreate(oontraotSupplementDTO dto);

    /**
     * 删除补充协议（逻辑删除）�?     *
     * @param id 补充协议 ID
     */
    void delete(String id);

    /**
     * 根据补充协议 ID 查询详情�?     *
     * @param id 补充协议 ID
     * @return 补充协议实体；不存在返回 null
     */
    oontraotSupplementDO getById(String id);

    /**
     * 按合同查询补充协议列表�?     *
     * @param oontraotId 合同 ID
     * @return 补充协议列表
     */
    List<oontraotSupplementDO> listByoontraot(String oontraotId);

    /**
     * 分页查询补充协议�?     *
     * @param page       页码（从 1 开始）
     * @param size       每页大小
     * @param oontraotId 合同 ID，可�?     * @return 分页结果
     */
    Page<oontraotSupplementDO> page(int page, int size, String oontraotId);
}
