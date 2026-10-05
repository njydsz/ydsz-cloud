package com.njydsz.cronjob.server.service.query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.cronjob.domain.constants.CronjobConstants;
import com.njydsz.cronjob.domain.converter.CronjobConverter;
import com.njydsz.cronjob.domain.dto.post.JobWebhookPostDTO;
import com.njydsz.cronjob.domain.dto.put.JobWebhookPutDTO;
import com.njydsz.cronjob.domain.enums.CronjobExceptionCode;
import com.njydsz.cronjob.domain.repository.JobWebhookRepository;
import com.njydsz.cronjob.domain.service.JobWebhookService;
import com.njydsz.cronjob.domain.vo.JobWebhookVO;
import com.njydsz.cronjob.server.core.dispatch.WebhookEventDispatcher;

/**
 * WebHook 事件订阅 Service 实现（server 层）。
 *
 * <p>CRUD + 测试推送，遵循 DDD 分层。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobWebhookServiceImpl implements JobWebhookService {

  private final JobWebhookRepository webhookRepository;
  private final WebhookEventDispatcher webhookEventDispatcher;

  @Override
  public String create(JobWebhookPostDTO dto) {
    JobWebhookVO vo = CronjobConverter.INSTANT.entityToVO(CronjobConverter.INSTANT.postDtoToEntity(dto));
    vo.setWebhookStatus(CronjobConstants.WEBHOOK_STATUS_ACTIVE);
    vo.setCreatedAt(LocalDateTime.now());
    vo.setUpdatedAt(LocalDateTime.now());
    if (vo.getHttpMethod() == null || vo.getHttpMethod().isBlank()) {
      vo.setHttpMethod(CronjobConstants.HTTP_METHOD_POST);
    }
    return webhookRepository.create(vo);
  }

  @Override
  public void update(JobWebhookPutDTO dto) {
    JobWebhookVO vo = CronjobConverter.INSTANT.entityToVO(CronjobConverter.INSTANT.putDtoToEntity(dto));
    vo.setUpdatedAt(LocalDateTime.now());
    webhookRepository.update(vo);
  }

  @Override
  public void delete(String id) {
    webhookRepository.deleteById(id, LocalDateTime.now());
  }

  @Override
  public PageResponse<List<JobWebhookVO>> page(int pageNum, int size, String eventType, String jobKey) {
    return webhookRepository.pageBy(pageNum, size, eventType, jobKey);
  }

  @Override
  public Optional<JobWebhookVO> findById(String id) {
    return webhookRepository.findById(id);
  }

  @Override
  public boolean testWebhook(String id) {
    Optional<JobWebhookVO> webhookOpt = webhookRepository.findById(id);
    if (webhookOpt.isEmpty()) {
      return false;
    }
    return webhookEventDispatcher.sendTest(webhookOpt.get());
  }
}
