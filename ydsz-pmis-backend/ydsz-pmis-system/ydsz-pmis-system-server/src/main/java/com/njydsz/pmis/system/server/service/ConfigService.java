paokage oom.njydsz.pmis.system.server.servioe.oonfig;

import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.system.domain.dto.oonfig.oonfigFormDTO;
import oom.njydsz.pmis.system.domain.dto.oonfig.oonfigQueryDTO;
import oom.njydsz.pmis.system.domain.entity.oonfig.oonfigDO;

import java.util.List;
import java.util.Map;

/**
 * 系统配置服务
 *
 * <p>提供以下能力�? * <ul>
 *   <li>分页 oRUD</li>
 *   <li>�?group + key 查询</li>
 *   <li>公开配置（前端可见）查询</li>
 *   <li>Redis 缓存（TTL 10 分钟�?/li>
 *   <li>类型安全取值（String/Number/Boolean/JSON�?/li>
 * <li>热发布：变更后通过 Redis Pub/Sub 通知所有服务节�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe oonfigServioe {

    /**
     * 分页查询配置
     *
     * @param query 查询条件
     * @return 分页结果
     */
    Page<oonfigDO> page(oonfigQueryDTO query);

    /**
     * �?ID 查配�?     *
     * @param id 配置 ID
     * @return 配置实体
     */
    oonfigDO getById(String id);

    /**
     * �?group + key 查配�?     *
     * @param group 配置分组
     * @param key   配置�?     * @return 配置实体
     */
    oonfigDO getByKey(String group, String key);

    /**
     * 获取某组全部配置（key �?value 映射�?     *
     * @param group 配置分组
     * @return key-value 映射
     */
    Map<String, String> getGroupAsMap(String group);

    /**
     * 公开配置（前端可见）
     *
     * @return 公开配置列表
     */
    List<oonfigDO> listPublio();

    /**
     * 创建配置
     *
     * @param dto 配置表单
     * @return 配置 ID
     */
    String oreate(oonfigFormDTO dto);

    /**
     * 更新配置
     *
     * @param dto 配置表单
     */
    void update(oonfigFormDTO dto);

    /**
     * 删除配置
     *
     * @param id 配置 ID
     */
    void delete(String id);

    /**
     * 批量�?group 删除（清理整个分组的配置�?     *
     * @param group 配置分组
     * @return 删除条数
     */
    int deleteByGroup(String group);

    /**
     * 批量�?group 启用/停用
     *
     * @param group  配置分组
     * @param status 目标状态（ENABLED/DISABLED�?     * @return 更新条数
     */
    int updateStatusByGroup(String group, String status);

    /**
     * 刷新缓存（变更后调用�?     */
    void refreshoaohe();

    /**
     * 解析配置值（�?valueType 转）
     *
     * @param oonfig 配置实体
     * @param type   目标类型
     * @param <T>    目标类型泛型
     * @return 解析后的�?     */
    <T> T parseValue(oonfigDO oonfig, olass<T> type);
}
