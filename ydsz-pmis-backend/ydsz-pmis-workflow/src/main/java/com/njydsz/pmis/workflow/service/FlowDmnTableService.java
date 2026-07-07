package com.njydsz.pmis.workflow.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.workflow.entity.FlowDmnTableDO;

import java.util.List;
import java.util.Map;

/**
 * DMN 决策表服务
 *
 * <p>P0-4: DMN 决策表引擎（对标 Camunda/Flowable DMN）。
 * 对外暴露：分页查询 / 详情 / 新建 / 更新 / 发布 / 执行决策。
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
public interface FlowDmnTableService {

    /**
     * 按 ID 获取决策表
     *
     * @param id 主键 ID
     * @return 决策表定义，不存在返回 null
     */
    FlowDmnTableDO getById(String id);

    /**
     * 按 tableKey 获取决策表
     *
     * @param tableKey 决策表唯一标识
     * @return 决策表定义，不存在返回 null
     */
    FlowDmnTableDO getByKey(String tableKey);

    /**
     * 分页查询决策表
     *
     * @param pageNum   页码（从 1 开始）
     * @param pageSize  每页大小
     * @param tableName 决策表名称模糊过滤（可空）
     * @return 分页结果
     */
    Page<FlowDmnTableDO> page(int pageNum, int pageSize, String tableName);

    /**
     * 新建决策表
     *
     * <p>校验 tableKey 唯一性，初始化版本号与状态。
     *
     * @param table 决策表定义
     * @return 新建后的主键 ID
     */
    String save(FlowDmnTableDO table);

    /**
     * 更新决策表
     *
     * @param table 决策表定义（需包含 id）
     */
    void update(FlowDmnTableDO table);

    /**
     * 发布决策表
     *
     * <p>将状态置为 PUBLISHED，版本号 +1。
     *
     * @param id 主键 ID
     */
    void publish(String id);

    /**
     * 执行决策
     *
     * <p>从 DB 加载决策表定义，反序列化 JSON 为内存模型，调用 DmnEngine 执行。
     *
     * @param tableKey 决策表唯一标识
     * @param context  输入上下文（变量名 → 值）
     * @return 输出结果列表（每个匹配规则产生一组输出）
     */
    List<Map<String, Object>> execute(String tableKey, Map<String, Object> context);
}
