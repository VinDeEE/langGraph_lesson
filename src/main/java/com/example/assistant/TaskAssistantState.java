package com.example.assistant;

import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.*;

/**
 * 智能任务助手状态
 */
public class TaskAssistantState extends AgentState {

    // 状态 key
    public static final String USER_INPUT = "userInput";
    public static final String MESSAGES = "messages";
    public static final String PARSED_TASK = "parsedTask";      // 解析出的任务信息
    public static final String TOOL_RESULTS = "toolResults";    // 工具调用结果
    public static final String FINAL_RESPONSE = "finalResponse"; // 最终回复
    public static final String NEXT_ACTION = "nextAction";      // 下一步动作

    public static final Map<String, Channel<?>> SCHEMA = Map.of(
        USER_INPUT, Channels.base(() -> ""),
        MESSAGES, Channels.appender(ArrayList::new),
        PARSED_TASK, Channels.base(() -> ""),
        TOOL_RESULTS, Channels.appender(ArrayList::new),
        FINAL_RESPONSE, Channels.base(() -> ""),
        NEXT_ACTION, Channels.base(() -> "")
    );

    public TaskAssistantState(Map<String, Object> initData) {
        super(initData);
    }

    public Optional<String> userInput() { return value(USER_INPUT); }
    public Optional<String> parsedTask() { return value(PARSED_TASK); }
    public Optional<String> finalResponse() { return value(FINAL_RESPONSE); }
    public Optional<String> nextAction() { return value(NEXT_ACTION); }
    public List<String> messages() { return this.<List<String>>value(MESSAGES).orElse(List.of()); }
    public List<String> toolResults() { return this.<List<String>>value(TOOL_RESULTS).orElse(List.of()); }
}
