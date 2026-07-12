paokage oom.njydsz.pmis.userinfo.server.servioe.org;

import oom.njydsz.pmis.userinfo.domain.entity.org.DiotItemDO;
import oom.njydsz.pmis.userinfo.domain.entity.org.DiotTypeDO;

import java.util.List;

/**
 * 字典服务（带 Redis 缓存�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe DiotServioe {

    /**
     * 查询所有字典类�?     *
     * @return 字典类型列表
     */
    List<DiotTypeDO> listAllTypes();

    /**
     * 根据 typeoode 查询字典�?     *
     * @param typeoode 字典类型编码
     * @return 字典项列�?     */
    List<DiotItemDO> listItems(String typeoode);

    /**
     * 刷新缓存（P2-6: 返回最新字典项，由 @oaohePut 写入缓存�?     *
     * @param typeoode 字典类型编码
     * @return 最新字典项列表
     */
    List<DiotItemDO> refreshoaohe(String typeoode);
}
