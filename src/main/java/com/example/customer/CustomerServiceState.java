package com.example.customer;

import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.*;

/**
 * 智能客服状态定义
 *
 * 在 LangGraph 中，State 是节点间共享的数据容器。
 * 所有节点都可以读取和更新状态。
 */
public class CustomerServiceState extends AgentState {

    // 状态 key 常量
    public static final String USER_MESSAGE = "userMessage";
    public static final String INTENT = "intent";
    public static final String KNOWLEDGE_RESULT = "knowledgeResult";
    public static final String RESPONSE = "response";
    public static final String MESSAGES = "messages";
    public static final String NEXT_NODE = "next_node";  // 用于动态路由

    /**
     * 定义状态的 Schema（数据结构）
     * 使用 Channel 来定义每个字段的行为：
     * - appender: 追加模式，新值会添加到列表中
     * - base: 基础模式，新值会替换旧值
     */
    public static final Map<String, Channel<?>> SCHEMA = Map.of(
        MESSAGES, Channels.appender(ArrayList::new),
        USER_MESSAGE, Channels.base(() -> ""),
        INTENT, Channels.base(() -> ""),
        KNOWLEDGE_RESULT, Channels.base(() -> ""),
        RESPONSE, Channels.base(() -> ""),
        NEXT_NODE, Channels.base(() -> "")
    );

    public CustomerServiceState(Map<String, Object> initData) {
        super(initData);
    }

    // 便捷的 getter 方法

    public Optional<String> userMessage() {
        return value(USER_MESSAGE);
    }

    public Optional<String> intent() {
        return value(INTENT);
    }

    public Optional<String> knowledgeResult() {
        return value(KNOWLEDGE_RESULT);
    }

    public Optional<String> response() {
        return value(RESPONSE);
    }

    public Optional<String> nextNode() {
        return value(NEXT_NODE);
    }

    @SuppressWarnings("unchecked")
    public List<String> messages() {
        return this.<List<String>>value(MESSAGES).orElse(List.of());
    }
}
