package com.njydsz.pmis.message.service;

import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.message.entity.MessageTemplateDO;
import com.njydsz.pmis.message.mapper.MessageTemplateMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("MessageTemplateServiceImpl 模板管理测试")
class MessageTemplateServiceImplTest {

    private MessageTemplateMapper mapper;
    private MessageTemplateServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(MessageTemplateMapper.class);
        service = new MessageTemplateServiceImpl(mapper);
    }

    @Test
    @DisplayName("创建模板 - 重复抛 DUPLICATE_KEY")
    void createDuplicate() {
        when(mapper.selectByCodeAndChannel("T", "SMS", 1L)).thenReturn(new MessageTemplateDO());
        MessageTemplateDO t = new MessageTemplateDO();
        t.setTemplateCode("T");
        t.setChannel("sms");
        t.setContent("x");
        assertThatThrownBy(() -> service.create(t))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(10102);
    }

    @Test
    @DisplayName("创建模板 - 缺少 templateCode 抛 BAD_REQUEST")
    void createMissingCode() {
        MessageTemplateDO t = new MessageTemplateDO();
        t.setContent("x");
        t.setChannel("SMS");
        assertThatThrownBy(() -> service.create(t))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("创建模板 - 缺少 content 抛 BAD_REQUEST")
    void createMissingContent() {
        MessageTemplateDO t = new MessageTemplateDO();
        t.setTemplateCode("T");
        t.setChannel("SMS");
        assertThatThrownBy(() -> service.create(t))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("update 模板不存在 - 抛 NOT_FOUND")
    void updateMissing() {
        when(mapper.selectById(anyLong())).thenReturn(null);
        MessageTemplateDO t = new MessageTemplateDO();
        t.setId(1L);
        assertThatThrownBy(() -> service.update(t))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(10101);
    }

    @Test
    @DisplayName("delete 不存在 - 抛 NOT_FOUND")
    void deleteMissing() {
        when(mapper.selectById(1L)).thenReturn(null);
        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("listByChannel - 转大写")
    void listByChannelUpper() {
        when(mapper.selectByChannel(anyString(), anyLong())).thenReturn(java.util.List.of());
        service.listByChannel("sms");
        verifyChannel();
    }

    private void verifyChannel() {
        org.mockito.Mockito.verify(mapper).selectByChannel(org.mockito.ArgumentMatchers.eq("SMS"), anyLong());
    }
}
