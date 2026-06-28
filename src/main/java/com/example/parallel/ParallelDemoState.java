package com.example.parallel;

import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.*;

/**
 * 并行执行 Demo 的状态
 *
 * 核心点：
 * - appender 类型的 Channel 用于收集多个并行节点的结果
 * - 每个并行节点写入自己的 key，避免冲突
 */
public class ParallelDemoState extends AgentState {

    public static final String USER_QUERY = "userQuery";
    public static final String MESSAGES = "messages";

    // 并行节点各自写入不同的 key
    public static final String KNOWLEDGE_RESULT = "knowledgeResult";    // 知识库结果
    public static final String HISTORY_RESULT = "historyResult";        // 历史记录结果
    public static final String RECOMMEND_RESULT = "recommendResult";    // 推荐结果

    // 合并后的结果
    public static final String MERGED_RESULT = "mergedResult";
    public static final String FINAL_RESPONSE = "finalResponse";

    public static final Map<String, Channel<?>> SCHEMA = Map.of(
        USER_QUERY, Channels.base(() -> ""),
        MESSAGES, Channels.appender(ArrayList::new),
        KNOWLEDGE_RESULT, Channels.base(() -> ""),
        HISTORY_RESULT, Channels.base(() -> ""),
        RECOMMEND_RESULT, Channels.base(() -> ""),
        MERGED_RESULT, Channels.base(() -> ""),
        FINAL_RESPONSE, Channels.base(() -> "")
    );

    public ParallelDemoState(Map<String, Object> initData) {
        super(initData);
    }

    public Optional<String> userQuery() { return value(USER_QUERY); }
    public Optional<String> knowledgeResult() { return value(KNOWLEDGE_RESULT); }
    public Optional<String> historyResult() { return value(HISTORY_RESULT); }
    public Optional<String> recommendResult() { return value(RECOMMEND_RESULT); }
    public Optional<String> mergedResult() { return value(MERGED_RESULT); }
    public Optional<String> finalResponse() { return value(FINAL_RESPONSE); }
    public List<String> messages() { return this.<List<String>>value(MESSAGES).orElse(List.of()); }
}
