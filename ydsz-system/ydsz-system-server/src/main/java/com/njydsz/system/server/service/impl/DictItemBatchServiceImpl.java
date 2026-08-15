package com.njydsz.system.server.service.impl;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.system.domain.dto.DictItemDTO;
import com.njydsz.system.domain.enums.SystemExceptionCode;
import com.njydsz.system.server.service.DictItemBatchService;
import com.njydsz.system.server.service.DictItemService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 字典项批量操作 Service 实现
 *
 * <p>提供批量新增能力。批量内任意一条失败则全部回滚（事务保证）。
 *
 * @author ydsz-team
 * @since 1.1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DictItemBatchServiceImpl implements DictItemBatchService {

    private final DictItemService dictItemService;

    /**
     * 批量新增字典项
     *
     * <p>执行链路：
     * <ol>
     *   <li>批量内去重：校验批量内无重复 (typeCode, itemCode)</li>
     *   <li>逐条插入（复用 DictItemService.save 的唯一性校验）</li>
     * </ol>
     *
     * <p><b>事务边界：</b>所有插入在同一事务内，任意一条失败则全部回滚。
     *
     * @param items 字典项列表
     * @return 操作结果 {successCount, failCount, message}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> batchSave(List<DictItemDTO> items) {
        if (items == null || items.isEmpty()) {
            throw BusinessException.of(SystemExceptionCode.PARAM_ERROR)
                    .data("reason", "字典项列表不能为空");
        }

        // 1. 批量内去重校验
        Set<String> innerKeySet = new HashSet<>();
        for (DictItemDTO item : items) {
            String key = item.getTypeCode() + "/" + item.getItemCode();
            if (!innerKeySet.add(key)) {
                throw BusinessException.of(SystemExceptionCode.DICT_ITEM_CODE_DUPLICATE)
                        .data("reason", "批量数据中存在重复项: " + key);
            }
        }

        // 2. 逐条插入
        int successCount = 0;
        for (DictItemDTO item : items) {
            try {
                dictItemService.save(item);
                successCount++;
            } catch (BusinessException e) {
                // 唯一性冲突，包装批量异常
                throw BusinessException.of(SystemExceptionCode.DICT_ITEM_CODE_DUPLICATE)
                        .data("reason", String.format("第 %d 条插入失败: %s/%s 已存在",
                                successCount + 1, item.getTypeCode(), item.getItemCode()));
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("successCount", successCount);
        result.put("totalCount", items.size());
        result.put("message", String.format("成功批量新增 %d 条字典项", successCount));
        return result;
    }
}
