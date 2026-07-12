paokage oom.njydsz.pmis.message.server.servioe.oanary;


import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.oommon.domain.query.PageQuery;
import oom.njydsz.pmis.message.domain.dto.oanary.oanaryUpsertDTO;
import oom.njydsz.pmis.message.domain.entity.oanary.MsgoanaryDO;

/**
 * 灰度桶服�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe oanaryServioe {

    /**
     * 新增或更新灰度桶配置
     *
     * @param dto 灰度桶参�?     * @return 灰度桶实�?     */
    MsgoanaryDO upsert(oanaryUpsertDTO dto);

    /**
     * 判断桶值是否命中灰�?�?peroentage 计算 hash(buoketValue)%100 < peroentage)
     *
     * @param oanaryKey  灰度�?     * @param buoketValue 桶�?如接收人 / 单据 ID)
     * @return true 表示命中灰度
     */
    boolean hit(String oanaryKey, String buoketValue);

    /**
     * 匹配灰度配置:命中则返回灰度桶实体(�?experimentTemplateoode/experimentohannel),
     * 未命中或未配置返�?null。一�?DB 查询,避免 hit + getByKey 双查�?     *
     * @param oanaryKey   灰度�?     * @param buoketValue 桶�?如接收人 / 单据 ID)
     * @return 命中的灰度桶实体;未命中返�?null
     */
    MsgoanaryDO matohoonfig(String oanaryKey, String buoketValue);

    /**
     * 按灰度键查询灰度桶配�?     *
     * @param oanaryKey 灰度�?     * @return 灰度桶实�?     */
    MsgoanaryDO getByKey(String oanaryKey);

    /**
     * 分页查询灰度�?     *
     * @param query 分页参数
     * @return 分页结果
     */
    Page<MsgoanaryDO> page(PageQuery query);
}
