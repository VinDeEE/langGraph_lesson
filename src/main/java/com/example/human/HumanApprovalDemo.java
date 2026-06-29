package com.example.human;

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
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;

/**
 * 人工介入 Demo
 *
 * 核心知识点：
 * 1. 使用 interruptBefore 在节点执行前暂停
 * 2. 暂停后等待人工输入
 * 3. 人工确认后继续执行
 *
 * 场景：敏感操作（如大额转账、删除数据）需要人工审批
 *
 * 流程：
 * 自动处理 → [暂停等待人工审批] → 人工确认 → 继续执行
 */
public class HumanApprovalDemo {

    private static final Logger log = LoggerFactory.getLogger(HumanApprovalDemo.class);

    // 状态定义
    static class ApprovalState extends AgentState {
        public static final String REQUEST = "request";
        public static final String AMOUNT = "amount";
        public static final String AUTO_REVIEW = "autoReview";
        public static final String HUMAN_APPROVED = "humanApproved";
        public static final String EXECUTION_RESULT = "executionResult";
        public static final String MESSAGES = "messages";

        public static final Map<String, Channel<?>> SCHEMA = Map.of(
            REQUEST, Channels.base(() -> ""),
            AMOUNT, Channels.base(() -> 0.0),
            AUTO_REVIEW, Channels.base(() -> ""),
            HUMAN_APPROVED, Channels.base(() -> false),
            EXECUTION_RESULT, Channels.base(() -> ""),
            MESSAGES, Channels.appender(ArrayList::new)
        );

        public ApprovalState(Map<String, Object> initData) { super(initData); }
        public Optional<String> request() { return value(REQUEST); }
        public Optional<Double> amount() { return value(AMOUNT); }
        public Optional<String> autoReview() { return value(AUTO_REVIEW); }
        public Optional<Boolean> humanApproved() { return value(HUMAN_APPROVED); }
        public Optional<String> executionResult() { return value(EXECUTION_RESULT); }
        public List<String> messages() { return this.<List<String>>value(MESSAGES).orElse(List.of()); }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("  人工介入 Demo - 敏感操作审批流程");
        System.out.println("=".repeat(60));

        // 创建 MemorySaver
        MemorySaver saver = new MemorySaver();

        // 创建图
        StateGraph<ApprovalState> stateGraph = new StateGraph<>(
            ApprovalState.SCHEMA,
            ApprovalState::new
        )
            .addNode("submit_request", node_async(state -> {
                String request = state.request().orElse("");
                log.info(">>> [提交请求] {}", request);
                return Map.of(
                    ApprovalState.MESSAGES, "【提交】请求已提交: " + request
                );
            }))
            .addNode("auto_review", node_async(state -> {
                double amount = state.amount().orElse(0.0);
                String request = state.request().orElse("");
                log.info(">>> [自动审核] 金额: {}, 请求: {}", amount, request);

                // 自动审核逻辑：
                // 1. 金额 >= 1000 需要人工审批
                // 2. 包含"删除"关键词需要人工审批
                String result = (amount >= 1000 || request.contains("删除"))
                    ? "NEED_HUMAN" : "AUTO_PASS";
                log.info(">>> [自动审核] 结果: {}", result);

                return Map.of(
                    ApprovalState.AUTO_REVIEW, result,
                    ApprovalState.MESSAGES, "【自动审核】" + result
                );
            }))
            // 核心：human_approval 节点设置了 interruptBefore
            // 框架会在执行这个节点前暂停，等待人工输入
            .addNode("human_approval", node_async(state -> {
                // 这个节点的代码在人工确认后才执行
                boolean approved = state.humanApproved().orElse(false);
                log.info(">>> [人工审批] 结果: {}", approved ? "通过" : "拒绝");

                return Map.of(
                    ApprovalState.MESSAGES, "【人工审批】" + (approved ? "通过" : "拒绝")
                );
            }))
            .addNode("execute", node_async(state -> {
                String request = state.request().orElse("");
                log.info(">>> [执行] 执行操作: {}", request);

                return Map.of(
                    ApprovalState.EXECUTION_RESULT, "操作已执行: " + request,
                    ApprovalState.MESSAGES, "【执行】操作完成"
                );
            }))
            .addNode("reject", node_async(state -> {
                log.info(">>> [拒绝] 请求被拒绝");
                return Map.of(
                    ApprovalState.EXECUTION_RESULT, "请求被拒绝",
                    ApprovalState.MESSAGES, "【拒绝】请求已拒绝"
                );
            }))
            .addEdge(START, "submit_request")
            .addEdge("submit_request", "auto_review")
            // 条件路由：根据自动审核结果决定下一步
            .addConditionalEdges("auto_review",
                edge_async(state -> {
                    String review = state.autoReview().orElse("NEED_HUMAN");
                    return review.equals("AUTO_PASS") ? "auto_pass" : "need_human";
                }),
                Map.of(
                    "auto_pass", "execute",
                    "need_human", "human_approval"
                )
            )
            // 人工审批后根据结果路由
            .addConditionalEdges("human_approval",
                edge_async(state -> {
                    boolean approved = state.humanApproved().orElse(false);
                    return approved ? "approved" : "rejected";
                }),
                Map.of(
                    "approved", "execute",
                    "rejected", "reject"
                )
            )
            .addEdge("execute", END)
            .addEdge("reject", END);

        // 编译时配置 interruptBefore
        CompileConfig compileConfig = CompileConfig.builder()
            .checkpointSaver(saver)
            .interruptBefore("human_approval")  // 核心：在这个节点前暂停
            .build();

        CompiledGraph<ApprovalState> graph = stateGraph.compile(compileConfig);

        // ========== 场景1：小额请求（自动通过）==========
        System.out.println("\n" + "=".repeat(50));
        System.out.println("场景1：小额请求（自动通过）");
        System.out.println("=".repeat(50));

        String threadId1 = "request-001";
        RunnableConfig config1 = RunnableConfig.builder().threadId(threadId1).build();

        Map<String, Object> inputs1 = Map.of(
            ApprovalState.REQUEST, "购买办公用品",
            ApprovalState.AMOUNT, 500.0
        );

        var result1 = graph.invoke(inputs1, config1);
        printResult(result1);

        // ========== 场景2：大额请求（需要人工审批）==========
        System.out.println("\n" + "=".repeat(50));
        System.out.println("场景2：大额请求（需要人工审批）");
        System.out.println("=".repeat(50));

        String threadId2 = "request-002";
        RunnableConfig config2 = RunnableConfig.builder().threadId(threadId2).build();

        Map<String, Object> inputs2 = Map.of(
            ApprovalState.REQUEST, "采购服务器设备",
            ApprovalState.AMOUNT, 50000.0
        );

        // 第一次执行：会在 human_approval 前暂停
        var result2 = graph.invoke(inputs2, config2);

        // 检查是否暂停了
        StateSnapshot<ApprovalState> snapshot = graph.getState(config2);
        System.out.println("\n当前状态（已暂停，等待人工审批）:");
        System.out.println("  请求: " + snapshot.state().request().orElse(""));
        System.out.println("  金额: " + snapshot.state().amount().orElse(0.0));
        System.out.println("  自动审核: " + snapshot.state().autoReview().orElse(""));
        System.out.println("\n>>> 等待人工审批... <<<");

        // 模拟人工审批：更新状态为"通过"
        Map<String, Object> humanApproval = Map.of(
            ApprovalState.HUMAN_APPROVED, true  // 人工批准
        );
        graph.updateState(config2, humanApproval);

        System.out.println("\n>>> 人工已审批：通过 <<<");

        // 恢复执行
        var result3 = graph.invoke(GraphInput.resume(), config2);
        printResult(result3);

        // ========== 场景3：人工拒绝 ==========
        System.out.println("\n" + "=".repeat(50));
        System.out.println("场景3：人工拒绝");
        System.out.println("=".repeat(50));

        String threadId3 = "request-003";
        RunnableConfig config3 = RunnableConfig.builder().threadId(threadId3).build();

        Map<String, Object> inputs3 = Map.of(
            ApprovalState.REQUEST, "删除所有数据",
            ApprovalState.AMOUNT, 0.0
        );

        // 第一次执行：暂停
        graph.invoke(inputs3, config3);

        // 模拟人工审批：拒绝
        graph.updateState(config3, Map.of(ApprovalState.HUMAN_APPROVED, false));
        System.out.println("\n>>> 人工已审批：拒绝 <<<");

        // 恢复执行
        var result4 = graph.invoke(GraphInput.resume(), config3);
        printResult(result4);
    }

    static void printResult(Optional<ApprovalState> result) {
        result.ifPresent(state -> {
            System.out.println("\n执行结果:");
            System.out.println("  " + state.executionResult().orElse("无"));
            System.out.println("\n执行日志:");
            state.messages().forEach(msg -> System.out.println("  " + msg));
        });
    }
}
