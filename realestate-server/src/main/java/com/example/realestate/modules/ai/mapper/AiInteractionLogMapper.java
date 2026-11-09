package com.example.realestate.modules.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.realestate.modules.ai.model.entity.AiInteractionLog;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;
import java.util.List;

public interface AiInteractionLogMapper extends BaseMapper<AiInteractionLog> {

    @Select("SELECT scene AS scene, status AS status, COUNT(*) AS cnt, " +
            "COALESCE(SUM(prompt_tokens),0) AS promptTokens, COALESCE(SUM(completion_tokens),0) AS completionTokens " +
            "FROM ai_interaction_log WHERE created_at >= #{from} AND created_at < #{to} " +
            "GROUP BY scene, status")
    List<SceneStatusCount> countBySceneAndStatus(@Param("from") Instant from, @Param("to") Instant to);

    @Select("SELECT latency_ms FROM ai_interaction_log WHERE created_at >= #{from} AND created_at < #{to} " +
            "AND latency_ms IS NOT NULL")
    List<Integer> selectLatencies(@Param("from") Instant from, @Param("to") Instant to);

    @Select("SELECT * FROM ai_interaction_log WHERE created_at >= #{from} AND created_at < #{to} " +
            "ORDER BY id DESC LIMIT 20")
    List<AiInteractionLog> selectRecent(@Param("from") Instant from, @Param("to") Instant to);

    record SceneStatusCount(String scene, String status, long cnt, long promptTokens, long completionTokens) {
    }
}
