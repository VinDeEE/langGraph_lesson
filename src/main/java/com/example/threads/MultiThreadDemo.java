package com.example.threads;

import org.bsc.langgraph4j.*;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import org.bsc.langgraph4j.state.StateSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;

/**
 * 多线程/会话管理 Demo
 *
 * 核心知识点：
 * 1. 不同 threadId 的会话完全隔离
 * 2. 每个会话有独立的检查点历史
 * 3. 多个会话可以并发执行
 *
 * 场景：多用户同时使用客服系统
 */
public class MultiThreadDemo {

    private static final Logger log = LoggerFactory.getLogger(MultiThreadDemo.class);

    // 状态定义
    static class ChatState extends AgentState {
        public static final String USER_ID = "userId";
        public static final String MESSAGE = "message";
        public static final String RESPONSE = "response";
        public static final String ROUND = "round";
        public static final String MESSAGES = "messages";

        public static final Map<String, Channel<?>> SCHEMA = Map.of(
            USER_ID, Channels.base(() -> ""),
            MESSAGE, Channels.base(() -> ""),
            RESPONSE, Channels.base(() -> ""),
            ROUND, Channels.base(() -> 0),
            MESSAGES, Channels.appender(ArrayList::new)
        );

        public ChatState(Map<String, Object> initData) { super(initData); }
        public Optional<String> userId() { return value(USER_ID); }
        public Optional<String> message() { return value(MESSAGE); }
        public Optional<String> response() { return value(RESPONSE); }
        public Optional<Integer> round() { return value(ROUND); }
        public List<String> messages() { return this.<List<String>>value(MESSAGES).orElse(List.of()); }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("  多线程/会话管理 Demo");
        System.out.println("=".repeat(60));

        // 创建共享的图和检查点保存器
        MemorySaver saver = new MemorySaver();

        StateGraph<ChatState> stateGraph = new StateGraph<>(
            ChatState.SCHEMA,
            ChatState::new
        )
            .addNode("process", node_async(state -> {
                String userId = state.userId().orElse("unknown");
                String message = state.message().orElse("");
                int round = state.round().orElse(0) + 1;

                log.info(">>> [用户 {}] 第 {} 轮, 消息: {}", userId, round, message);

                // 模拟处理延迟
                Thread.sleep(100);

                String response = "用户" + userId + "的第" + round + "轮回复: 收到「" + message + "」";

                return Map.of(
                    ChatState.RESPONSE, response,
                    ChatState.ROUND, round,
                    ChatState.MESSAGES, "【第" + round + "轮】" + message
                );
            }))
            .addEdge(START, "process")
            .addEdge("process", END);

        CompileConfig config = CompileConfig.builder()
            .checkpointSaver(saver)
            .build();

        CompiledGraph<ChatState> graph = stateGraph.compile(config);

        // ========== 演示1：会话隔离 ==========
        System.out.println("\n--- 演示1：会话隔离 ---\n");

        // 用户 A 的会话
        String userA = "user-A";
        RunnableConfig configA = RunnableConfig.builder().threadId(userA).build();

        runRound(graph, configA, userA, "你好，我是用户A");
        runRound(graph, configA, userA, "我想咨询订单问题");

        // 用户 B 的会话
        String userB = "user-B";
        RunnableConfig configB = RunnableConfig.builder().threadId(userB).build();

        runRound(graph, configB, userB, "你好，我是用户B");
        runRound(graph, configB, userB, "我想退货");

        // 查看各自的会话历史
        System.out.println("\n--- 会话历史对比 ---\n");

        System.out.println("用户 A 的历史:");
        printHistory(graph, configA);

        System.out.println("\n用户 B 的历史:");
        printHistory(graph, configB);

        // ========== 演示2：并发执行 ==========
        System.out.println("\n--- 演示2：并发执行 ---\n");

        ExecutorService executor = Executors.newFixedThreadPool(3);
        List<Future<?>> futures = new ArrayList<>();

        // 同时启动 3 个用户的会话
        for (int i = 1; i <= 3; i++) {
            String userId = "concurrent-user-" + i;
            RunnableConfig userConfig = RunnableConfig.builder().threadId(userId).build();

            futures.add(executor.submit(() -> {
                try {
                    runRound(graph, userConfig, userId, "我是" + userId + "的消息1");
                    runRound(graph, userConfig, userId, "我是" + userId + "的消息2");
                } catch (Exception e) {
                    log.error("用户 {} 执行异常", userId, e);
                }
            }));
        }

        // 等待所有任务完成
        for (Future<?> future : futures) {
            future.get();
        }
        executor.shutdown();

        System.out.println("\n所有并发会话完成！");

        // 查看每个并发会话的历史
        System.out.println("\n--- 并发会话历史 ---\n");
        for (int i = 1; i <= 3; i++) {
            String userId = "concurrent-user-" + i;
            RunnableConfig userConfig = RunnableConfig.builder().threadId(userId).build();
            System.out.println(userId + " 的历史:");
            printHistory(graph, userConfig);
            System.out.println();
        }
    }

    static void runRound(CompiledGraph<ChatState> graph, RunnableConfig config,
                          String userId, String message) throws Exception {
        Map<String, Object> inputs = Map.of(
            ChatState.USER_ID, userId,
            ChatState.MESSAGE, message
        );

        var result = graph.invoke(inputs, config);
        result.ifPresent(state -> {
            System.out.printf("[%s] %s → %s%n", userId, message, state.response().orElse(""));
        });
    }

    static void printHistory(CompiledGraph<ChatState> graph, RunnableConfig config) {
        try {
            Collection<StateSnapshot<ChatState>> history = graph.getStateHistory(config);
            for (StateSnapshot<ChatState> snapshot : history) {
                int round = snapshot.state().round().orElse(0);
                String msg = snapshot.state().message().orElse("");
                String resp = snapshot.state().response().orElse("");
                if (round > 0) {
                    System.out.printf("  第%d轮: %s → %s%n", round, msg, resp);
                }
            }
        } catch (Exception e) {
            System.out.println("  无历史记录");
        }
    }
}
