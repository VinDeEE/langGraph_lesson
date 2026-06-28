package com.example.checkpoint;

import org.bsc.langgraph4j.*;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import org.bsc.langgraph4j.state.StateSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;

/**
 * 检查点 Demo
 *
 * 核心知识点：
 * 1. MemorySaver - 内存中保存状态快照
 * 2. RunnableConfig - 配置 threadId 标识会话
 * 3. stateHistory() - 获取状态历史
 * 4. 从检查点恢复执行
 *
 * 场景：多轮对话中，保存每轮状态，可以回溯到任意轮次
 */
public class CheckpointDemo {

    private static final Logger log = LoggerFactory.getLogger(CheckpointDemo.class);

    // 状态定义
    static class ChatState extends AgentState {
        public static final String USER_INPUT = "userInput";
        public static final String RESPONSE = "response";
        public static final String ROUND = "round";
        public static final String MESSAGES = "messages";

        public static final Map<String, Channel<?>> SCHEMA = Map.of(
            USER_INPUT, Channels.base(() -> ""),
            RESPONSE, Channels.base(() -> ""),
            ROUND, Channels.base(() -> 0),
            MESSAGES, Channels.appender(ArrayList::new)
        );

        public ChatState(Map<String, Object> initData) { super(initData); }
        public Optional<String> userInput() { return value(USER_INPUT); }
        public Optional<String> response() { return value(RESPONSE); }
        public Optional<Integer> round() { return value(ROUND); }
        public List<String> messages() { return this.<List<String>>value(MESSAGES).orElse(List.of()); }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("  检查点 Demo");
        System.out.println("=".repeat(60));

        // 1. 创建 MemorySaver（检查点保存器）
        MemorySaver saver = new MemorySaver();

        // 2. 创建图，配置检查点
        StateGraph<ChatState> stateGraph = new StateGraph<>(
            ChatState.SCHEMA,
            ChatState::new
        )
            .addNode("process", node_async(state -> {
                String input = state.userInput().orElse("");
                int round = state.round().orElse(0) + 1;
                log.info(">>> [处理] 第 {} 轮, 输入: {}", round, input);

                // 模拟处理
                String response = "回复第" + round + "轮: 收到「" + input + "」";

                return Map.of(
                    ChatState.RESPONSE, response,
                    ChatState.ROUND, round,
                    ChatState.MESSAGES, "【第" + round + "轮】" + input + " → " + response
                );
            }))
            .addEdge(START, "process")
            .addEdge("process", END);

        // 3. 编译时配置检查点
        CompileConfig config = CompileConfig.builder()
            .checkpointSaver(saver)
            .build();

        CompiledGraph<ChatState> graph = stateGraph.compile(config);

        // 4. 模拟多轮对话
        String threadId = "user-session-001";  // 会话 ID

        System.out.println("\n--- 模拟 3 轮对话 ---\n");

        // 第 1 轮
        runRound(graph, threadId, "你好");

        // 第 2 轮
        runRound(graph, threadId, "今天天气怎么样？");

        // 第 3 轮
        runRound(graph, threadId, "推荐一个餐厅");

        // 5. 查看状态历史
        System.out.println("\n--- 查看状态历史 ---\n");

        RunnableConfig sessionConfig = RunnableConfig.builder()
            .threadId(threadId)
            .build();

        List<StateSnapshot<ChatState>> history = new ArrayList<>(graph.getStateHistory(sessionConfig));
        System.out.println("历史记录数量: " + history.size());

        for (int i = 0; i < history.size(); i++) {
            StateSnapshot<ChatState> snapshot = history.get(i);
            System.out.printf("\n历史 #%d:%n", i);
            System.out.printf("  轮次: %s%n", snapshot.state().round().orElse(0));
            System.out.printf("  输入: %s%n", snapshot.state().userInput().orElse(""));
            System.out.printf("  回复: %s%n", snapshot.state().response().orElse(""));
        }

        // 6. 从检查点恢复（回溯到第 2 轮）
        System.out.println("\n--- 从检查点恢复（回溯到第 2 轮）---\n");

        if (history.size() >= 2) {
            StateSnapshot<ChatState> checkpoint = history.get(1);  // 第 2 轮
            System.out.println("恢复到: 第 " + checkpoint.state().round().orElse(0) + " 轮");
            System.out.println("当时的输入: " + checkpoint.state().userInput().orElse(""));
            System.out.println("当时的回复: " + checkpoint.state().response().orElse(""));
        }

        System.out.println("\n演示完成！");
    }

    /**
     * 执行一轮对话
     */
    static void runRound(CompiledGraph<ChatState> graph, String threadId, String userInput) {
        // 配置会话 ID
        RunnableConfig config = RunnableConfig.builder()
            .threadId(threadId)
            .build();

        Map<String, Object> inputs = Map.of(
            ChatState.USER_INPUT, userInput
        );

        // 执行（检查点会自动保存）
        var result = graph.invoke(inputs, config);

        result.ifPresent(state -> {
            System.out.printf("轮次 %d: %s → %s%n",
                state.round().orElse(0),
                userInput,
                state.response().orElse(""));
        });
    }
}
