package com.example.realestate.modules.content.service.impl;

import com.example.realestate.common.api.PageResponse;
import com.example.realestate.common.enums.ContentStatus;
import com.example.realestate.common.enums.ErrorCode;
import com.example.realestate.common.enums.GuideCategory;
import com.example.realestate.common.enums.PolicyType;
import com.example.realestate.common.exception.BusinessException;
import com.example.realestate.modules.content.mapper.GuideArticleMapper;
import com.example.realestate.modules.content.mapper.PolicyDocumentMapper;
import com.example.realestate.modules.content.model.dto.GuideCreateRequest;
import com.example.realestate.modules.content.model.dto.GuideUpdateRequest;
import com.example.realestate.modules.content.model.dto.PolicyCreateRequest;
import com.example.realestate.modules.content.model.dto.PolicyUpdateRequest;
import com.example.realestate.modules.content.model.entity.GuideArticle;
import com.example.realestate.modules.content.model.entity.PolicyDocument;
import com.example.realestate.modules.content.model.query.ContentQuery;
import com.example.realestate.modules.content.model.vo.GuideDetailVO;
import com.example.realestate.modules.content.model.vo.GuideListItemVO;
import com.example.realestate.modules.content.model.vo.GuideListRow;
import com.example.realestate.modules.content.model.vo.PolicyDetailVO;
import com.example.realestate.modules.content.model.vo.PolicyListItemVO;
import com.example.realestate.modules.content.model.vo.PolicyListRow;
import com.example.realestate.modules.content.service.ContentService;
import com.example.realestate.modules.file.service.PrivateStorageService;
import com.example.realestate.security.CurrentUserProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;

/**
 * 内容实现：
 *  - 公开访问（游客/客户）只返回 PUBLISHED；ADMIN 可见全部并可按状态筛选；
 *  - 编辑/删除仅 DRAFT/OFFLINE，已发布需先下架，避免“已发布内容被就地改写”；
 *  - 正文按纯文本原样存储与返回，不使用富文本编辑器，前端按换行渲染。
 */
@Service
public class ContentServiceImpl implements ContentService {

    private static final Logger log = LoggerFactory.getLogger(ContentServiceImpl.class);

    private final GuideArticleMapper guideMapper;
    private final PolicyDocumentMapper policyMapper;
    private final PrivateStorageService privateStorage;
    private final CurrentUserProvider currentUser;

    public ContentServiceImpl(GuideArticleMapper guideMapper,
                              PolicyDocumentMapper policyMapper,
                              PrivateStorageService privateStorage,
                              CurrentUserProvider currentUser) {
        this.guideMapper = guideMapper;
        this.policyMapper = policyMapper;
        this.privateStorage = privateStorage;
        this.currentUser = currentUser;
    }

    // ---------- 教程 / 网签指引 / 公告 ----------

    @Override
    public PageResponse<GuideListItemVO> listGuides(ContentQuery query) {
        ContentQuery safe = prepareQuery(query, true);
        boolean publicOnly = !currentUser.isAdmin();
        long total = guideMapper.countByQuery(safe, publicOnly);
        long size = safe.normalizedSize();
        if (total == 0) {
            return PageResponse.of(List.of(), 0, safe.normalizedPage(), size);
        }
        List<GuideListItemVO> items = guideMapper
                .selectPageList(safe, publicOnly, safe.offset(), size)
                .stream()
                .map(this::fillGuideLabels)
                .toList();
        return PageResponse.of(items, total, safe.normalizedPage(), size);
    }

    @Override
    public GuideDetailVO getGuide(Long id) {
        GuideArticle entity = requireGuide(id);
        requirePublicVisible(entity.getStatus());
        return toGuideDetail(entity);
    }

    @Override
    @Transactional
    public GuideDetailVO createGuide(GuideCreateRequest request) {
        Instant now = Instant.now();
        GuideArticle entity = new GuideArticle();
        entity.setTitle(request.title().trim());
        entity.setCategory(request.category().name());
        entity.setSummary(trimToNull(request.summary()));
        entity.setContent(request.content());
        entity.setStatus(ContentStatus.DRAFT.name());
        entity.setSort(request.sort() == null ? 0 : request.sort());
        entity.setVersion(0);
        entity.setDeleted(0);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        guideMapper.insert(entity);
        log.info("内容创建 id={} category={} operator={}", entity.getId(), entity.getCategory(),
                currentUser.currentUserIdOrNull());
        return toGuideDetail(entity);
    }

    @Override
    @Transactional
    public GuideDetailVO updateGuide(Long id, GuideUpdateRequest request) {
        GuideArticle existing = requireGuide(id);
        requireEditable(existing.getStatus(), "内容");
        GuideArticle update = new GuideArticle();
        update.setId(id);
        if (request.title() != null && !request.title().isBlank()) {
            update.setTitle(request.title().trim());
        }
        if (request.category() != null) {
            update.setCategory(request.category().name());
        }
        if (request.summary() != null) {
            update.setSummary(trimToNull(request.summary()));
        }
        if (request.content() != null && !request.content().isBlank()) {
            update.setContent(request.content());
        }
        if (request.sort() != null) {
            update.setSort(request.sort());
        }
        update.setVersion(request.version() != null ? request.version() : existing.getVersion());
        update.setUpdatedAt(Instant.now());
        int rows = guideMapper.updateById(update);
        if (rows == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "内容已被他人修改，请刷新后重试");
        }
        log.info("内容更新 id={} operator={}", id, currentUser.currentUserIdOrNull());
        return toGuideDetail(requireGuide(id));
    }

    @Override
    @Transactional
    public GuideDetailVO publishGuide(Long id) {
        requireGuide(id);
        if (guideMapper.publish(id, Instant.now()) == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "内容当前状态不允许发布");
        }
        log.info("内容发布 id={} operator={}", id, currentUser.currentUserIdOrNull());
        return toGuideDetail(requireGuide(id));
    }

    @Override
    @Transactional
    public GuideDetailVO offlineGuide(Long id) {
        requireGuide(id);
        if (guideMapper.offline(id, Instant.now()) == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "仅已发布内容可以下架");
        }
        log.info("内容下架 id={} operator={}", id, currentUser.currentUserIdOrNull());
        return toGuideDetail(requireGuide(id));
    }

    @Override
    @Transactional
    public void deleteGuide(Long id) {
        GuideArticle existing = requireGuide(id);
        requireEditable(existing.getStatus(), "内容");
        guideMapper.deleteById(id);
        log.info("内容逻辑删除 id={} operator={}", id, currentUser.currentUserIdOrNull());
    }

    // ---------- 政策文档 ----------

    @Override
    public PageResponse<PolicyListItemVO> listPolicies(ContentQuery query) {
        ContentQuery safe = prepareQuery(query, false);
        boolean publicOnly = !currentUser.isAdmin();
        long total = policyMapper.countByQuery(safe, publicOnly);
        long size = safe.normalizedSize();
        if (total == 0) {
            return PageResponse.of(List.of(), 0, safe.normalizedPage(), size);
        }
        List<PolicyListItemVO> items = policyMapper
                .selectPageList(safe, publicOnly, safe.offset(), size)
                .stream()
                .map(this::fillPolicyLabels)
                .toList();
        return PageResponse.of(items, total, safe.normalizedPage(), size);
    }

    @Override
    public PolicyDetailVO getPolicy(Long id) {
        PolicyDocument entity = requirePolicy(id);
        requirePublicVisible(entity.getStatus());
        return toPolicyDetail(entity);
    }

    @Override
    @Transactional
    public PolicyDetailVO createPolicy(PolicyCreateRequest request) {
        Instant now = Instant.now();
        PolicyDocument entity = new PolicyDocument();
        entity.setTitle(request.title().trim());
        entity.setDocNo(trimToNull(request.docNo()));
        entity.setDocType(request.docType().name());
        entity.setSource(trimToNull(request.source()));
        entity.setPublishDate(request.publishDate());
        entity.setEffectiveDate(request.effectiveDate());
        entity.setContent(request.content());
        entity.setStatus(ContentStatus.DRAFT.name());
        entity.setVersion(0);
        entity.setDeleted(0);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        policyMapper.insert(entity);
        log.info("政策创建 id={} type={} operator={}", entity.getId(), entity.getDocType(),
                currentUser.currentUserIdOrNull());
        return toPolicyDetail(entity);
    }

    @Override
    @Transactional
    public PolicyDetailVO updatePolicy(Long id, PolicyUpdateRequest request) {
        PolicyDocument existing = requirePolicy(id);
        requireEditable(existing.getStatus(), "政策");
        PolicyDocument update = new PolicyDocument();
        update.setId(id);
        if (request.title() != null && !request.title().isBlank()) {
            update.setTitle(request.title().trim());
        }
        if (request.docNo() != null) {
            update.setDocNo(trimToNull(request.docNo()));
        }
        if (request.docType() != null) {
            update.setDocType(request.docType().name());
        }
        if (request.source() != null) {
            update.setSource(trimToNull(request.source()));
        }
        if (request.publishDate() != null) {
            update.setPublishDate(request.publishDate());
        }
        if (request.effectiveDate() != null) {
            update.setEffectiveDate(request.effectiveDate());
        }
        if (request.content() != null && !request.content().isBlank()) {
            update.setContent(request.content());
        }
        update.setVersion(request.version() != null ? request.version() : existing.getVersion());
        update.setUpdatedAt(Instant.now());
        int rows = policyMapper.updateById(update);
        if (rows == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "政策已被他人修改，请刷新后重试");
        }
        log.info("政策更新 id={} operator={}", id, currentUser.currentUserIdOrNull());
        return toPolicyDetail(requirePolicy(id));
    }

    @Override
    @Transactional
    public PolicyDetailVO publishPolicy(Long id) {
        requirePolicy(id);
        if (policyMapper.publish(id, Instant.now()) == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "政策当前状态不允许发布");
        }
        log.info("政策发布 id={} operator={}", id, currentUser.currentUserIdOrNull());
        return toPolicyDetail(requirePolicy(id));
    }

    @Override
    @Transactional
    public PolicyDetailVO offlinePolicy(Long id) {
        requirePolicy(id);
        if (policyMapper.offline(id, Instant.now()) == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "仅已发布政策可以下架");
        }
        log.info("政策下架 id={} operator={}", id, currentUser.currentUserIdOrNull());
        return toPolicyDetail(requirePolicy(id));
    }

    @Override
    @Transactional
    public void deletePolicy(Long id) {
        PolicyDocument existing = requirePolicy(id);
        requireEditable(existing.getStatus(), "政策");
        policyMapper.deleteById(id);
        log.info("政策逻辑删除 id={} operator={}", id, currentUser.currentUserIdOrNull());
    }

    @Override
    @Transactional
    public PolicyDetailVO uploadPolicyAttachment(Long id, MultipartFile file) {
        PolicyDocument existing = requirePolicy(id);
        requireEditable(existing.getStatus(), "政策");
        String key = privateStorage.storePolicyAttachment(file);
        String oldKey = existing.getAttachmentKey();
        try {
            policyMapper.updateAttachment(id, key, Instant.now());
        } catch (RuntimeException e) {
            // 数据库写入失败时补偿删除新附件，避免孤儿对象
            privateStorage.delete(key);
            throw e;
        }
        if (oldKey != null && !oldKey.equals(key)) {
            privateStorage.delete(oldKey);
        }
        log.info("政策附件上传 id={} key={} operator={}", id, key, currentUser.currentUserIdOrNull());
        return toPolicyDetail(requirePolicy(id));
    }

    @Override
    public PolicyAttachment downloadPolicyAttachment(Long id) {
        PolicyDocument entity = requirePolicy(id);
        requirePublicVisible(entity.getStatus());
        if (entity.getAttachmentKey() == null || entity.getAttachmentKey().isBlank()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "该政策没有附件");
        }
        byte[] content = privateStorage.read(entity.getAttachmentKey(), "政策附件不存在");
        return new PolicyAttachment(content, "policy-" + id + ".pdf");
    }

    // ---------- 内部方法 ----------

    private ContentQuery prepareQuery(ContentQuery query, boolean guide) {
        ContentQuery safe = query == null ? new ContentQuery() : query;
        if (safe.getKeyword() != null) {
            String keyword = safe.getKeyword().trim();
            if (keyword.length() > 64) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "搜索关键字过长");
            }
            safe.setKeyword(keyword.isEmpty() ? null : keyword);
        }
        if (safe.getStatus() != null && !safe.getStatus().isBlank()) {
            ContentStatus status = ContentStatus.of(safe.getStatus());
            if (status == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "内容状态不合法");
            }
            safe.setStatus(status.name());
        } else {
            safe.setStatus(null);
        }
        if (safe.getCategory() != null && !safe.getCategory().isBlank()) {
            if (guide) {
                GuideCategory category = GuideCategory.of(safe.getCategory());
                if (category == null) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "内容分类不合法");
                }
                safe.setCategory(category.name());
            } else {
                PolicyType type = PolicyType.of(safe.getCategory());
                if (type == null) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "政策类型不合法");
                }
                safe.setCategory(type.name());
            }
        } else {
            safe.setCategory(null);
        }
        return safe;
    }

    private GuideArticle requireGuide(Long id) {
        GuideArticle entity = id == null ? null : guideMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "内容不存在");
        }
        return entity;
    }

    private PolicyDocument requirePolicy(Long id) {
        PolicyDocument entity = id == null ? null : policyMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "政策不存在");
        }
        return entity;
    }

    /** 非 ADMIN 只能看到已发布内容，草稿/下架一律 404 */
    private void requirePublicVisible(String status) {
        if (currentUser.isAdmin()) {
            return;
        }
        if (ContentStatus.of(status) != ContentStatus.PUBLISHED) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "内容不存在");
        }
    }

    private void requireEditable(String status, String label) {
        if (ContentStatus.of(status) == ContentStatus.PUBLISHED) {
            throw new BusinessException(ErrorCode.CONFLICT, "已发布" + label + "需先下架再编辑");
        }
    }

    private GuideDetailVO toGuideDetail(GuideArticle entity) {
        return new GuideDetailVO(entity.getId(), entity.getTitle(), entity.getCategory(),
                categoryLabel(entity.getCategory()), entity.getSummary(), entity.getContent(),
                entity.getStatus(), statusLabel(entity.getStatus()), entity.getSort(),
                entity.getVersion(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private GuideListItemVO fillGuideLabels(GuideListRow row) {
        return new GuideListItemVO(row.getId(), row.getTitle(), row.getCategory(),
                categoryLabel(row.getCategory()), row.getSummary(), row.getStatus(),
                statusLabel(row.getStatus()), row.getSort(), row.getVersion(),
                row.getCreatedAt(), row.getUpdatedAt());
    }

    private PolicyDetailVO toPolicyDetail(PolicyDocument entity) {
        return new PolicyDetailVO(entity.getId(), entity.getTitle(), entity.getDocNo(), entity.getDocType(),
                policyTypeLabel(entity.getDocType()), entity.getSource(), entity.getPublishDate(),
                entity.getEffectiveDate(), entity.getContent(), entity.getStatus(),
                statusLabel(entity.getStatus()),
                entity.getAttachmentKey() != null && !entity.getAttachmentKey().isBlank(),
                entity.getVersion(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private PolicyListItemVO fillPolicyLabels(PolicyListRow row) {
        return new PolicyListItemVO(row.getId(), row.getTitle(), row.getDocNo(), row.getDocType(),
                policyTypeLabel(row.getDocType()), row.getSource(), row.getPublishDate(),
                row.getEffectiveDate(), row.getStatus(), statusLabel(row.getStatus()),
                Boolean.TRUE.equals(row.getHasAttachment()), row.getVersion(),
                row.getCreatedAt(), row.getUpdatedAt());
    }

    private String categoryLabel(String category) {
        GuideCategory parsed = GuideCategory.of(category);
        return parsed == null ? category : parsed.label();
    }

    private String policyTypeLabel(String docType) {
        PolicyType parsed = PolicyType.of(docType);
        return parsed == null ? docType : parsed.label();
    }

    private String statusLabel(String status) {
        ContentStatus parsed = ContentStatus.of(status);
        return parsed == null ? status : parsed.label();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
