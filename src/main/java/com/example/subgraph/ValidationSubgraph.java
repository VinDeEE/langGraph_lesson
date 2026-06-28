package com.example.subgraph;

import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;

/**
 * 订单验证子图
 *
 * 核心点：
 * 1. 子图是一个独立的 StateGraph，有自己的节点和边
 * 2. 子图可以编译成 CompiledGraph，然后嵌入到父图中
 * 3. 子图与父图共享同一个 State 类型
 *
 * 子图内部流程：
 * 检查库存 → 检查用户信用 → 验证完成
 */
public class ValidationSubgraph {

    private static final Logger log = LoggerFactory.getLogger(ValidationSubgraph.class);

    /**
     * 创建验证子图
     *
     * 核心点：返回编译后的 CompiledGraph，父图可以直接使用
     */
    public static CompiledGraph<OrderState> create() throws Exception {
        StateGraph<OrderState> graph = new StateGraph<>(
            OrderState.SCHEMA,
            OrderState::new
        )
            .addNode("check_inventory", node_async(new CheckInventoryNode()))
            .addNode("check_user_credit", node_async(new CheckUserCreditNode()))
            .addNode("validation_complete", node_async(new ValidationCompleteNode()))

            .addEdge(START, "check_inventory")
            .addEdge("check_inventory", "check_user_credit")
            .addEdge("check_user_credit", "validation_complete")
            .addEdge("validation_complete", END);

        return graph.compile();
    }

    /**
     * 检查库存节点
     */
    static class CheckInventoryNode implements NodeAction<OrderState> {
        @Override
        public Map<String, Object> apply(OrderState state) throws Exception {
            String orderId = state.orderId().orElse("unknown");
            log.info(">>> [验证子图] 检查库存, 订单: {}", orderId);

            // 模拟库存检查
            return Map.of(
                OrderState.CURRENT_STEP, "check_inventory",
                OrderState.MESSAGES, "【库存检查】库存充足"
            );
        }
    }

    /**
     * 检查用户信用节点
     */
    static class CheckUserCreditNode implements NodeAction<OrderState> {
        @Override
        public Map<String, Object> apply(OrderState state) throws Exception {
            String userId = state.userId().orElse("unknown");
            log.info(">>> [验证子图] 检查用户信用, 用户: {}", userId);

            // 模拟信用检查
            return Map.of(
                OrderState.CURRENT_STEP, "check_user_credit",
                OrderState.MESSAGES, "【信用检查】用户信用良好"
            );
        }
    }

    /**
     * 验证完成节点
     */
    static class ValidationCompleteNode implements NodeAction<OrderState> {
        @Override
        public Map<String, Object> apply(OrderState state) throws Exception {
            log.info(">>> [验证子图] 验证完成");

            return Map.of(
                OrderState.VALIDATION_RESULT, "PASS",
                OrderState.CURRENT_STEP, "validation_complete",
                OrderState.MESSAGES, "【验证完成】所有检查通过"
            );
        }
    }
}
