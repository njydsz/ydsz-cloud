package com.njydsz.system.server.service;

import java.util.List;

import com.njydsz.system.domain.vo.FrontendInitVO;

/**
 * 前端初始化服务接口
 *
 * <p>提供前端初始化所需数据的聚合查询能力，减少前端启动时的请求次数。
 *
 * @author ydsz-team
 * @since 1.9.0
 */
public interface FrontendInitService {

  /**
   * 获取前端初始化数据
   *
   * <p>聚合返回公开配置、常用字典等前端启动所需数据。
   *
   * @return 前端初始化聚合数据
   */
  FrontendInitVO getInitData();

  /**
   * 获取指定字典类型的初始化数据
   *
   * <p>按类型编码查询常用的字典项，用于前端下拉框数据源。
   *
   * @param typeCodes 字典类型编码列表
   * @return 前端初始化聚合数据（含指定字典）
   */
  FrontendInitVO getInitDataWithDicts(List<String> typeCodes);
}
