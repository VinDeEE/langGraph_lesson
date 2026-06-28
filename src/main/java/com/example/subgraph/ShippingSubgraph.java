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
 * 发货处理子图
 *
 * 子图内部流程：
 * 创建物流单 → 通知仓库 → 发货完成
 */
public class ShippingSubgraph {

    private static final Logger log = LoggerFactory.getLogger(ShippingSubgraph.class);

    public static CompiledGraph<OrderState> create() throws Exception {
        StateGraph<OrderState> graph = new StateGraph<>(
            OrderState.SCHEMA,
            OrderState::new
        )
            .addNode("create_tracking", node_async(new CreateTrackingNode()))
            .addNode("notify_warehouse", node_async(new NotifyWarehouseNode()))
            .addNode("shipping_complete", node_async(new ShippingCompleteNode()))

            .addEdge(START, "create_tracking")
            .addEdge("create_tracking", "notify_warehouse")
            .addEdge("notify_warehouse", "shipping_complete")
            .addEdge("shipping_complete", END);

        return graph.compile();
    }

    static class CreateTrackingNode implements NodeAction<OrderState> {
        @Override
        public Map<String, Object> apply(OrderState state) throws Exception {
            String orderId = state.orderId().orElse("unknown");
            log.info(">>> [发货子图] 创建物流单, 订单: {}", orderId);

            return Map.of(
                OrderState.CURRENT_STEP, "create_tracking",
                OrderState.MESSAGES, "【物流】物流单已创建: SF" + System.currentTimeMillis()
            );
        }
    }

    static class NotifyWarehouseNode implements NodeAction<OrderState> {
        @Override
        public Map<String, Object> apply(OrderState state) throws Exception {
            log.info(">>> [发货子图] 通知仓库");

            return Map.of(
                OrderState.CURRENT_STEP, "notify_warehouse",
                OrderState.MESSAGES, "【仓库】已通知仓库备货"
            );
        }
    }

    static class ShippingCompleteNode implements NodeAction<OrderState> {
        @Override
        public Map<String, Object> apply(OrderState state) throws Exception {
            log.info(">>> [发货子图] 发货完成");

            return Map.of(
                OrderState.SHIPPING_RESULT, "SHIPPED",
                OrderState.CURRENT_STEP, "shipping_complete",
                OrderState.MESSAGES, "【发货完成】商品已发出"
            );
        }
    }
}
