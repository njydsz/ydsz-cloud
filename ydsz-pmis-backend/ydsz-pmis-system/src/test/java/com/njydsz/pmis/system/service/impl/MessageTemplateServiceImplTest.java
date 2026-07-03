package com.njydsz.pmis.system.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.system.entity.MessageTemplateDO;
import com.njydsz.pmis.system.mapper.MessageTemplateMapper;
import com.njydsz.pmis.system.service.MessageTemplateServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MessageTemplateServiceImpl 单元测试")
class MessageTemplateServiceImplTest {

    @Mock
    private MessageTemplateMapper templateMapper;

    @InjectMocks
    private MessageTemplateServiceImpl templateService;

    @Nested
    @DisplayName("create 方法")
    class CreateTest {

        @Test
        @DisplayName("创建模板成功时应返回模板 ID")
        void shouldCreateTemplateSuccessfully() {
            MessageTemplateDO t = new MessageTemplateDO();
            t.setTemplateCode("WELCOME");
            t.setChannel("EMAIL");
            t.setContent("Hello ${name}");

            when(templateMapper.selectByCodeAndChannel("WELCOME", "EMAIL", 1L)).thenReturn(null);
            doAnswer(invocation -> {
                MessageTemplateDO entity = invocation.getArgument(0);
                entity.setId(100L);
                return 1;
            }).when(templateMapper).insert(any(MessageTemplateDO.class));

            Long id = templateService.create(t);

            assertThat(id).isEqualTo(100L);
            verify(templateMapper).insert(any(MessageTemplateDO.class));
        }

        @Test
        @DisplayName("templateCode 为空时应抛出异常")
        void shouldThrowWhenTemplateCodeIsEmpty() {
            MessageTemplateDO t = new MessageTemplateDO();
            t.setChannel("EMAIL");
            t.setContent("content");

            assertThatThrownBy(() -> templateService.create(t))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("error.message.msg_f68a3fa3");
        }

        @Test
        @DisplayName("模板已存在时应抛出异常")
        void shouldThrowWhenTemplateExists() {
            MessageTemplateDO t = new MessageTemplateDO();
            t.setTemplateCode("WELCOME");
            t.setChannel("EMAIL");
            t.setContent("Hello");

            MessageTemplateDO existing = new MessageTemplateDO();
            when(templateMapper.selectByCodeAndChannel("WELCOME", "EMAIL", 1L)).thenReturn(existing);

            assertThatThrownBy(() -> templateService.create(t))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("error.message.msg_74548ac6");
        }
    }

    @Nested
    @DisplayName("update 方法")
    class UpdateTest {

        @Test
        @DisplayName("更新模板成功时应调用 mapper.updateById")
        void shouldUpdateTemplateSuccessfully() {
            MessageTemplateDO t = new MessageTemplateDO();
            t.setId(1L);
            t.setContent("new content");

            MessageTemplateDO existing = new MessageTemplateDO();
            existing.setId(1L);
            when(templateMapper.selectById(1L)).thenReturn(existing);
            when(templateMapper.updateById(any(MessageTemplateDO.class))).thenReturn(1);

            assertThatCode(() -> templateService.update(t)).doesNotThrowAnyException();
            verify(templateMapper).updateById(any(MessageTemplateDO.class));
        }

        @Test
        @DisplayName("ID 为空时应抛出异常")
        void shouldThrowWhenIdIsNull() {
            MessageTemplateDO t = new MessageTemplateDO();

            assertThatThrownBy(() -> templateService.update(t))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("error.message.msg_ff1828c0");
        }
    }

    @Nested
    @DisplayName("delete 方法")
    class DeleteTest {

        @Test
        @DisplayName("删除模板成功时应调用 mapper.deleteById")
        void shouldDeleteTemplateSuccessfully() {
            MessageTemplateDO existing = new MessageTemplateDO();
            existing.setId(1L);
            when(templateMapper.selectById(1L)).thenReturn(existing);
            when(templateMapper.deleteById(1L)).thenReturn(1);

            assertThatCode(() -> templateService.delete(1L)).doesNotThrowAnyException();
            verify(templateMapper).deleteById(1L);
        }

        @Test
        @DisplayName("模板不存在时应抛出异常")
        void shouldThrowWhenTemplateNotFound() {
            when(templateMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> templateService.delete(999L))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("error.message.msg_246b57f0");
        }
    }

    @Nested
    @DisplayName("getById 方法")
    class GetByIdTest {

        @Test
        @DisplayName("模板存在时应返回模板")
        void shouldReturnTemplateWhenExists() {
            MessageTemplateDO t = new MessageTemplateDO();
            t.setId(1L);
            t.setTemplateCode("WELCOME");
            when(templateMapper.selectById(1L)).thenReturn(t);

            MessageTemplateDO result = templateService.getById(1L);

            assertThat(result).isNotNull();
            assertThat(result.getTemplateCode()).isEqualTo("WELCOME");
        }

        @Test
        @DisplayName("模板不存在时应抛出异常")
        void shouldThrowWhenNotFound() {
            when(templateMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> templateService.getById(999L))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("error.message.msg_246b57f0");
        }
    }

    @Nested
    @DisplayName("page 方法")
    class PageTest {

        @Test
        @DisplayName("分页查询应返回正确结果")
        void shouldReturnPagedTemplates() {
            when(templateMapper.selectPage(any(Page.class), any())).thenReturn(new Page<>());

            Page<MessageTemplateDO> result = templateService.page(1, 10, "EMAIL", null);

            assertThat(result).isNotNull();
            verify(templateMapper).selectPage(any(Page.class), any());
        }
    }

    @Test
    @DisplayName("按通道列出模板应返回正确结果")
    void shouldListByChannel() {
        MessageTemplateDO t = new MessageTemplateDO();
        t.setId(1L);
        t.setTemplateCode("WELCOME");
        when(templateMapper.selectByChannel("EMAIL", 1L)).thenReturn(List.of(t));

        List<MessageTemplateDO> result = templateService.listByChannel("EMAIL");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTemplateCode()).isEqualTo("WELCOME");
    }
}