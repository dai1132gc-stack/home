package com.example.realestate.modules.content.service.impl;

import com.example.realestate.common.enums.ErrorCode;
import com.example.realestate.common.enums.GuideCategory;
import com.example.realestate.common.enums.PolicyType;
import com.example.realestate.common.exception.BusinessException;
import com.example.realestate.modules.content.mapper.GuideArticleMapper;
import com.example.realestate.modules.content.mapper.PolicyDocumentMapper;
import com.example.realestate.modules.content.model.dto.GuideCreateRequest;
import com.example.realestate.modules.content.model.dto.GuideUpdateRequest;
import com.example.realestate.modules.content.model.entity.GuideArticle;
import com.example.realestate.modules.content.model.entity.PolicyDocument;
import com.example.realestate.modules.file.service.PrivateStorageService;
import com.example.realestate.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("内容服务：发布可见性、编辑约束与纯文本")
class ContentServiceImplTest {

    @Mock
    private GuideArticleMapper guideMapper;
    @Mock
    private PolicyDocumentMapper policyMapper;
    @Mock
    private PrivateStorageService privateStorage;
    @Mock
    private CurrentUserProvider currentUser;

    private ContentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ContentServiceImpl(guideMapper, policyMapper, privateStorage, currentUser);
    }

    private GuideArticle guide(Long id, String status) {
        GuideArticle entity = new GuideArticle();
        entity.setId(id);
        entity.setTitle("测试教程");
        entity.setCategory(GuideCategory.GUIDE.name());
        entity.setSummary("摘要");
        entity.setContent("第一行\n第二行\n<script>alert(1)</script>");
        entity.setStatus(status);
        entity.setSort(0);
        entity.setVersion(0);
        entity.setCreatedAt(Instant.parse("2026-11-08T02:00:00Z"));
        entity.setUpdatedAt(Instant.parse("2026-11-08T02:00:00Z"));
        return entity;
    }

    @Test
    @DisplayName("公开访问草稿返回 404，ADMIN 可见")
    void draftVisibility() {
        when(guideMapper.selectById(1L)).thenReturn(guide(1L, "DRAFT"));

        when(currentUser.isAdmin()).thenReturn(false);
        assertThatThrownBy(() -> service.getGuide(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);

        when(currentUser.isAdmin()).thenReturn(true);
        assertThat(service.getGuide(1L).status()).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("已发布内容禁止直接编辑，需先下架（409）")
    void publishedCannotEdit() {
        when(guideMapper.selectById(1L)).thenReturn(guide(1L, "PUBLISHED"));

        assertThatThrownBy(() -> service.updateGuide(1L, new GuideUpdateRequest("新标题", null, null, null, null, 0)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.CONFLICT);
        verify(guideMapper, never()).updateById(any(GuideArticle.class));
    }

    @Test
    @DisplayName("下架后可编辑，version 冲突返回 409")
    void offlineEditableWithVersion() {
        when(guideMapper.selectById(1L)).thenReturn(guide(1L, "OFFLINE"));
        when(guideMapper.updateById(any(GuideArticle.class))).thenReturn(0);

        assertThatThrownBy(() -> service.updateGuide(1L, new GuideUpdateRequest("新标题", null, null, null, null, 0)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    @DisplayName("创建内容固定为 DRAFT，正文按纯文本保存")
    void createDraftKeepsPlainText() {
        when(guideMapper.insert(any(GuideArticle.class))).thenAnswer(invocation -> {
            GuideArticle entity = invocation.getArgument(0);
            entity.setId(11L);
            return 1;
        });

        service.createGuide(new GuideCreateRequest(" 新教程 ", GuideCategory.GUIDE, " 摘要 ",
                "第一行\n第二行", 5));

        ArgumentCaptor<GuideArticle> captor = ArgumentCaptor.forClass(GuideArticle.class);
        verify(guideMapper).insert(captor.capture());
        GuideArticle saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo("DRAFT");
        assertThat(saved.getTitle()).isEqualTo("新教程");
        assertThat(saved.getContent()).isEqualTo("第一行\n第二行");
        assertThat(saved.getSort()).isEqualTo(5);
    }

    @Test
    @DisplayName("发布时条件更新 0 行返回 409")
    void publishConflict() {
        when(guideMapper.selectById(1L)).thenReturn(guide(1L, "PUBLISHED"));
        when(guideMapper.publish(eq(1L), any())).thenReturn(0);

        assertThatThrownBy(() -> service.publishGuide(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    @DisplayName("已发布内容禁止删除")
    void publishedCannotDelete() {
        when(guideMapper.selectById(1L)).thenReturn(guide(1L, "PUBLISHED"));

        assertThatThrownBy(() -> service.deleteGuide(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.CONFLICT);
        verify(guideMapper, never()).deleteById(1L);
    }

    @Test
    @DisplayName("无附件政策下载返回 404")
    void policyWithoutAttachment() {
        PolicyDocument policy = new PolicyDocument();
        policy.setId(2L);
        policy.setTitle("示例政策");
        policy.setDocType(PolicyType.POLICY.name());
        policy.setContent("正文");
        policy.setStatus("PUBLISHED");
        when(policyMapper.selectById(2L)).thenReturn(policy);
        when(currentUser.isAdmin()).thenReturn(false);

        assertThatThrownBy(() -> service.downloadPolicyAttachment(2L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }
}
