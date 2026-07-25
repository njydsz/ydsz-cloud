package com.njydsz.system.server.service;

import java.util.List;

import com.njydsz.system.domain.entity.DictVersionDO;

/**
 * 字典版本 Service。
 *
 * <p>提供字典变更版本记录和查询能力，支持回滚审计。
 *
 * @author ydsz-team
 */
public interface DictVersionService {

    /**
     * 按类型编码查询版本历史。
     *
     * @param typeCode 字典类型编码
     * @return 版本列表（按生效时间倒序）
     */
    List<DictVersionDO> listByTypeCode(String typeCode);

    /**
     * 创建版本快照。
     *
     * @param typeCode      字典类型编码
     * @param version       版本号
     * @param changeLog     变更说明
     * @return 版本记录 ID
     */
    String createVersion(String typeCode, String version, String changeLog);
}
