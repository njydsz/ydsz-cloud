package com.njydsz.pmis.message.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.entity.PageQuery;
import com.njydsz.pmis.message.dto.CanaryUpsertDTO;
import com.njydsz.pmis.message.entity.MsgCanaryDO;

/**
 * 灰度桶服务
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface CanaryService {

    /**
     * 新增或更新灰度桶配置
     *
     * @param dto 灰度桶参数
     * @return 灰度桶实体
     */
    MsgCanaryDO upsert(CanaryUpsertDTO dto);

    /**
     * 判断桶值是否命中灰度(按 percentage 计算 hash(bucketValue)%100 < percentage)
     *
     * @param canaryKey  灰度键
     * @param bucketValue 桶值(如接收人 / 单据 ID)
     * @return true 表示命中灰度
     */
    boolean hit(String canaryKey, String bucketValue);

    /**
     * 按灰度键查询灰度桶配置
     *
     * @param canaryKey 灰度键
     * @return 灰度桶实体
     */
    MsgCanaryDO getByKey(String canaryKey);

    /**
     * 分页查询灰度桶
     *
     * @param query 分页参数
     * @return 分页结果
     */
    Page<MsgCanaryDO> page(PageQuery query);
}
