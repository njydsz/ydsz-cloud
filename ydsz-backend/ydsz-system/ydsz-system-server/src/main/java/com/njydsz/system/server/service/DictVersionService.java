package com.njydsz.system.server.service;

import java.util.List;

import com.njydsz.system.domain.vo.DictVersionVO;

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
    List<DictVersionVO> listByTypeCode(String typeCode);

    /**
     * 创建版本快照。
     *
     * @param typeCode      字典类型编码
     * @param version       版本号
     * @param changeLog     变更说明
     * @param snapshotJson  字典项列表 JSON 快照（可为 null）
     * @return 版本记录 ID
     */
    String createVersion(String typeCode, String version, String changeLog, String snapshotJson);
}
