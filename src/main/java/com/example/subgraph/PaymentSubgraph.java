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
 * 支付处理子图
 *
 * 核心点：
 * 1. 每个子图负责一个独立的业务流程
 * 2. 子图可以被多个父图复用
 * 3. 子图内部可以有复杂的逻辑，对外只暴露输入输出
 *
 * 子图内部流程：
 * 扣款 → 记录支付 → 支付完成
 */
public class PaymentSubgraph {

    private static final Logger log = LoggerFactory.getLogger(PaymentSubgraph.class);

    public static CompiledGraph<OrderState> create() throws Exception {
        StateGraph<OrderState> graph = new StateGraph<>(
            OrderState.SCHEMA,
            OrderState::new
        )
            .addNode("deduct_payment", node_async(new DeductPaymentNode()))
            .addNode("record_payment", node_async(new RecordPaymentNode()))
            .addNode("payment_complete", node_async(new PaymentCompleteNode()))

            .addEdge(START, "deduct_payment")
            .addEdge("deduct_payment", "record_payment")
            .addEdge("record_payment", "payment_complete")
            .addEdge("payment_complete", END);

        return graph.compile();
    }

    static class DeductPaymentNode implements NodeAction<OrderState> {
        @Override
        public Map<String, Object> apply(OrderState state) throws Exception {
            double amount = state.amount().orElse(0.0);
            log.info(">>> [支付子图] 扣款: ¥{}", amount);

            return Map.of(
                OrderState.CURRENT_STEP, "deduct_payment",
                OrderState.MESSAGES, "【扣款】已扣款 ¥" + amount
            );
        }
    }

    static class RecordPaymentNode implements NodeAction<OrderState> {
        @Override
        public Map<String, Object> apply(OrderState state) throws Exception {
            log.info(">>> [支付子图] 记录支付信息");

            return Map.of(
                OrderState.CURRENT_STEP, "record_payment",
                OrderState.MESSAGES, "【记录】支付记录已保存"
            );
        }
    }

    static class PaymentCompleteNode implements NodeAction<OrderState> {
        @Override
        public Map<String, Object> apply(OrderState state) throws Exception {
            log.info(">>> [支付子图] 支付完成");

            return Map.of(
                OrderState.PAYMENT_RESULT, "SUCCESS",
                OrderState.CURRENT_STEP, "payment_complete",
                OrderState.MESSAGES, "【支付完成】支付成功"
            );
        }
    }
}
