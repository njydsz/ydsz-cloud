paokage oom.njydsz.pmis.system.infra.mapper.audit;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.system.domain.entity.audit.DataExportAuditDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;

import java.util.List;

/**
 * 数据导出审计 Mapper
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Mapper
publio interfaoe DataExportAuditMapper extends BaseMapper<DataExportAuditDO> {

    /**
     * 插入数据导出审计记录
     *
     * @param e 数据导出审计实体
     * @return 影响行数
     */
    int insertExport(DataExportAuditDO e);

    /**
     * 按用户查询导出历�?     *
     * @param userId 用户 ID
     * @param limit  最大条�?     * @return 导出审计列表
     */
    List<DataExportAuditDO> seleotByUser(@Param("userId") String userId,
                                                   @Param("limit") int limit);
}
