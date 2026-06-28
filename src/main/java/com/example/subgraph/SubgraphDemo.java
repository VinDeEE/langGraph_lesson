package com.example.subgraph;

import dev.langchain4j.model.openai.OpenAiChatModel;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;

/**
 * 子图 Demo
 *
 * 核心知识点：
 * 1. 子图是独立的 StateGraph，编译后嵌入父图
 * 2. 子图与父图共享同一个 State 类型
 * 3. 子图实现模块化、可复用的业务流程
 *
 * 架构：
 * ┌─────────────────────────────────────────────────────────┐
 * │                     父图（主流程）                        │
 * │                                                         │
 * │  接收订单 → ┌─────────────┐ → ┌─────────────┐ → ┌─────┐│
 * │            │ 验证子图     │   │ 支付子图     │   │发货  ││
 * │            │ (3个节点)    │   │ (3个节点)    │   │子图  ││
 * │            └─────────────┘   └─────────────┘   └─────┘│
 * │                                                         │
 * └─────────────────────────────────────────────────────────┘
 *
 * 优势：
 * - 每个子图独立开发、独立测试
 * - 子图可以在多个父图中复用
 * - 主流程清晰，只关注业务编排
 */
public class SubgraphDemo {

    private static final Logger log = LoggerFactory.getLogger(SubgraphDemo.class);

    public static void main(String[] args) throws Exception {
        // 不需要 LLM，纯流程演示
        runOrderProcess();
    }

    private static void runOrderProcess() throws Exception {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("  子图 Demo - 订单处理流程");
        System.out.println("=".repeat(60));

        // 1. 创建子图（编译后的 CompiledGraph）
        log.info("创建子图...");
        CompiledGraph<OrderState> validationSubgraph = ValidationSubgraph.create();
        CompiledGraph<OrderState> paymentSubgraph = PaymentSubgraph.create();
        CompiledGraph<OrderState> shippingSubgraph = ShippingSubgraph.create();

        // 2. 创建父图，将子图作为节点嵌入
        log.info("创建父图，嵌入子图...");
        StateGraph<OrderState> parentGraph = new StateGraph<>(
            OrderState.SCHEMA,
            OrderState::new
        )
            // 核心：使用 addNode(id, compiledGraph) 嵌入子图
            // 子图内部的节点会自动执行，对外只表现为一个节点
            .addNode("receive_order", node_async(new ReceiveOrderNode()))
            .addNode("validation", validationSubgraph)  // 嵌入验证子图
            .addNode("payment", paymentSubgraph)        // 嵌入支付子图
            .addNode("shipping", shippingSubgraph)      // 嵌入发货子图
            .addNode("order_complete", node_async(new OrderCompleteNode()))

            // 父图的流程：接收 → 验证 → 支付 → 发货 → 完成
            .addEdge(START, "receive_order")
            .addEdge("receive_order", "validation")
            .addEdge("validation", "payment")
            .addEdge("payment", "shipping")
            .addEdge("shipping", "order_complete")
            .addEdge("order_complete", END);

        // 3. 编译父图
        CompiledGraph<OrderState> compiledParent = parentGraph.compile();
        log.info("父图编译完成");

        // 4. 执行
        Map<String, Object> inputs = Map.of(
            OrderState.ORDER_ID, "ORD-2024-001",
            OrderState.USER_ID, "USER-123",
            OrderState.AMOUNT, 299.99
        );

        System.out.println("\n订单信息:");
        System.out.println("  订单号: ORD-2024-001");
        System.out.println("  用户ID: USER-123");
        System.out.println("  金额: ¥299.99");
        System.out.println("\n执行流程:");

        // 流式执行，观察每个节点（包括子图内部节点）的执行
        List<String> executionPath = new ArrayList<>();
        for (var state : compiledParent.stream(inputs)) {
            executionPath.add(state.node());
            log.info("节点 [{}] 执行完成", state.node());
        }

        System.out.println("\n执行路径: " + String.join(" → ", executionPath));

        // 获取最终状态
        var finalState = compiledParent.invoke(inputs);
        finalState.ifPresent(state -> {
            System.out.println("\n执行日志:");
            state.messages().forEach(msg -> System.out.println("  " + msg));

            System.out.println("\n最终结果:");
            System.out.println("  验证: " + state.validationResult().orElse("N/A"));
            System.out.println("  支付: " + state.paymentResult().orElse("N/A"));
            System.out.println("  发货: " + state.shippingResult().orElse("N/A"));
        });
    }

    /**
     * 接收订单节点（父图自己的节点）
     */
    static class ReceiveOrderNode implements NodeAction<OrderState> {
        @Override
        public Map<String, Object> apply(OrderState state) throws Exception {
            String orderId = state.orderId().orElse("unknown");
            log.info(">>> [父图] 接收订单: {}", orderId);

            return Map.of(
                OrderState.CURRENT_STEP, "receive_order",
                OrderState.MESSAGES, "【接收订单】订单 " + orderId + " 已接收"
            );
        }
    }

    /**
     * 订单完成节点（父图自己的节点）
     */
    static class OrderCompleteNode implements NodeAction<OrderState> {
        @Override
        public Map<String, Object> apply(OrderState state) throws Exception {
            log.info(">>> [父图] 订单处理完成");

            return Map.of(
                OrderState.CURRENT_STEP, "order_complete",
                OrderState.MESSAGES, "【完成】订单处理完成，等待收货"
            );
        }
    }
}
