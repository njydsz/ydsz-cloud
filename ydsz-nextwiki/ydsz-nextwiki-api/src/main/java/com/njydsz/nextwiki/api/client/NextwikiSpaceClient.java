package com.njydsz.nextwiki.api.client;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.feign.FeignClientConstants;
import com.njydsz.nextwiki.api.fallback.NextwikiSpaceClientFallback;
import com.njydsz.nextwiki.domain.vo.SpaceVO;

/**
 * 知识库空间查询 Feign 客户端（供跨服务调用）。
 *
 * <p>提供知识库空间的远程查询能力，典型场景：
 *
 * <ul>
 *   <li>工作流模块：在审批流程中嵌入知识库文档时，按空间 ID 反查空间信息
 *   <li>其他模块：按空间 ID 批量获取空间 VO 用于前端展示
 * </ul>
 *
 * <p>所有 ID 均为 String 类型（雪花算法字符串），与项目 ID 约定一致。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@FeignClient(
    name = FeignClientConstants.NEXTWIKI,
    contextId = "nextwikiSpaceClient",
    fallbackFactory = NextwikiSpaceClientFallback.class)
public interface NextwikiSpaceClient {

  /**
   * 按空间 ID 查询空间详情。
   *
   * @param spaceId 空间 ID
   * @return 空间 VO
   */
  @GetMapping(FeignClientConstants.NEXTWIKI_PATH_SPACE_GET)
  YdszResponse<SpaceVO> getSpaceById(@RequestParam String spaceId);

  /**
   * 批量按空间 ID 查询空间详情。
   *
   * @param spaceIds 空间 ID 列表
   * @return 空间 VO 列表
   */
  @PostMapping(FeignClientConstants.NEXTWIKI_PATH_SPACE_BATCH)
  YdszResponse<List<SpaceVO>> batchGetSpaces(@RequestBody List<String> spaceIds);
}
