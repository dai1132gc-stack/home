package com.example.realestate.modules.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.realestate.common.enums.ContentStatus;
import com.example.realestate.config.AiProperties;
import com.example.realestate.modules.ai.client.AiClient;
import com.example.realestate.modules.ai.client.AiUpstreamException;
import com.example.realestate.modules.ai.mapper.AiInteractionLogMapper;
import com.example.realestate.modules.ai.util.EmbeddingCodec;
import com.example.realestate.modules.ai.util.PolicyChunker;
import com.example.realestate.modules.content.mapper.PolicyDocumentChunkMapper;
import com.example.realestate.modules.content.mapper.PolicyDocumentMapper;
import com.example.realestate.modules.content.model.entity.PolicyDocument;
import com.example.realestate.modules.content.model.entity.PolicyDocumentChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 政策向量索引（W6）：
 * - 发布成功后 afterCommit 异步触发（不在发布事务内调用外部 AI）；索引失败不回滚发布；
 * - 下架/删除时删除分块；重建幂等（先删后插）；
 * - embedding 批量调用；内存缓存召回候选，索引变更即失效。
 */
@Service
public class PolicyIndexService {

    private static final Logger log = LoggerFactory.getLogger(PolicyIndexService.class);

    private final PolicyDocumentMapper policyMapper;
    private final PolicyDocumentChunkMapper chunkMapper;
    private final AiClient aiClient;
    private final AiProperties properties;
    private final AiInteractionLogger interactionLogger;

    /** 召回候选缓存（含向量）；null 表示失效 */
    private final AtomicReference<List<PolicyDocumentChunkMapper.IndexedChunkRow>> cache = new AtomicReference<>();

    public PolicyIndexService(PolicyDocumentMapper policyMapper, PolicyDocumentChunkMapper chunkMapper,
                              AiClient aiClient, AiProperties properties,
                              AiInteractionLogger interactionLogger) {
        this.policyMapper = policyMapper;
        this.chunkMapper = chunkMapper;
        this.aiClient = aiClient;
        this.properties = properties;
        this.interactionLogger = interactionLogger;
    }

    /** 在事务提交后索引；无事务时直接执行。供发布/更新调用。 */
    public void indexAfterCommit(Long policyId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    indexPolicySafely(policyId);
                }
            });
        } else {
            indexPolicySafely(policyId);
        }
    }

    /** 在事务提交后移除分块；供下架/删除调用。 */
    public void removeAfterCommit(Long policyId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    removeChunks(policyId);
                }
            });
        } else {
            removeChunks(policyId);
        }
    }

    /** 单条重建（幂等）；返回重建分块数 */
    public int reindex(Long policyId) {
        return indexPolicy(policyId);
    }

    /** 全量重建（幂等）；返回 [政策数, 成功分块数, 失败政策数] */
    public long[] reindexAll() {
        QueryWrapper<PolicyDocument> wrapper = new QueryWrapper<>();
        wrapper.select("id").eq("status", ContentStatus.PUBLISHED.name());
        List<PolicyDocument> published = policyMapper.selectList(wrapper);
        long chunks = 0;
        long failed = 0;
        for (PolicyDocument doc : published) {
            try {
                chunks += indexPolicy(doc.getId());
            } catch (RuntimeException e) {
                failed++;
                log.warn("政策索引重建失败 id={}: {}", doc.getId(), e.getMessage());
            }
        }
        return new long[]{published.size(), chunks, failed};
    }

    /** 索引失败不回滚发布：捕获一切异常，仅记录日志与统计 */
    private void indexPolicySafely(Long policyId) {
        try {
            indexPolicy(policyId);
        } catch (RuntimeException e) {
            log.warn("政策发布后索引失败（不影响发布）id={}: {}", policyId, e.getMessage());
        }
    }

    private int indexPolicy(Long policyId) {
        long start = System.currentTimeMillis();
        removeChunks(policyId);
        if (!aiClient.available()) {
            interactionLogger.record(AiInteractionLogger.SCENE_INDEX, null, "ANON",
                    AiInteractionLogger.STATUS_DEGRADED, null, null,
                    elapsed(start), null, null, AiHouseSearchService.DEGRADE_DISABLED);
            return 0;
        }
        PolicyDocument doc = policyMapper.selectById(policyId);
        if (doc == null || !ContentStatus.PUBLISHED.name().equals(doc.getStatus())) {
            return 0;
        }
        List<String> chunks = PolicyChunker.chunk(doc.getContent());
        if (chunks.isEmpty()) {
            return 0;
        }
        try {
            List<float[]> vectors = aiClient.embed(chunks);
            Instant now = Instant.now();
            List<PolicyDocumentChunk> rows = new ArrayList<>(chunks.size());
            for (int i = 0; i < chunks.size(); i++) {
                PolicyDocumentChunk row = new PolicyDocumentChunk();
                row.setPolicyId(policyId);
                row.setChunkIndex(i);
                row.setContent(chunks.get(i));
                row.setEmbedding(EmbeddingCodec.encode(vectors.get(i)));
                row.setEmbeddingModel(aiClient.embeddingModel());
                row.setCreatedAt(now);
                row.setUpdatedAt(now);
                rows.add(row);
            }
            rows.forEach(chunkMapper::insert);
            cache.set(null);
            interactionLogger.record(AiInteractionLogger.SCENE_INDEX, null, "ANON",
                    AiInteractionLogger.STATUS_SUCCESS, aiClient.provider(), aiClient.embeddingModel(),
                    elapsed(start), AiAccessGuard.estimateTokens(String.join("", chunks)), null, null);
            return rows.size();
        } catch (AiUpstreamException e) {
            interactionLogger.record(AiInteractionLogger.SCENE_INDEX, null, "ANON",
                    AiInteractionLogger.STATUS_FAILED, aiClient.provider(), aiClient.embeddingModel(),
                    elapsed(start), null, null, e.kind().name());
            throw e;
        }
    }

    private void removeChunks(Long policyId) {
        QueryWrapper<PolicyDocumentChunk> wrapper = new QueryWrapper<>();
        wrapper.eq("policy_id", policyId);
        chunkMapper.delete(wrapper);
        cache.set(null);
    }

    /** 召回候选（内存缓存；模型名与当前配置一致才有效） */
    public List<PolicyDocumentChunkMapper.IndexedChunkRow> indexedChunks() {
        List<PolicyDocumentChunkMapper.IndexedChunkRow> cached = cache.get();
        if (cached != null) {
            return cached;
        }
        List<PolicyDocumentChunkMapper.IndexedChunkRow> rows = chunkMapper.selectIndexedChunks();
        String model = aiClient.available() ? aiClient.embeddingModel() : null;
        if (model != null) {
            rows = rows.stream().filter(r -> model.equals(r.embeddingModel())).toList();
        }
        cache.set(rows);
        if (rows.size() > 1000) {
            log.warn("政策分块数量超过 1000（{}），建议关注内存占用", rows.size());
        }
        return rows;
    }

    public long countIndexed() {
        return chunkMapper.countIndexed();
    }

    private static int elapsed(long start) {
        return (int) Math.min(Integer.MAX_VALUE, System.currentTimeMillis() - start);
    }
}
