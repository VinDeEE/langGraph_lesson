package com.example.customer;

import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 路由节点
 *
 * 根据意图决定下一步应该路由到哪个处理节点
 * 通过在状态中写入 next_node 字段来告诉框架下一步去哪里
 */
public class RoutingNode implements NodeAction<CustomerServiceState> {

    private static final Logger log = LoggerFactory.getLogger(RoutingNode.class);

    // 定义路由目标的 key
    public static final String NEXT_NODE = "next_node";

    @Override
    public Map<String, Object> apply(CustomerServiceState state) {
        String intent = state.intent().orElse("OTHER");

        String nextNode = switch (intent) {
            case "COMPLAINT" -> "complaint_handler";
            case "RETURN_EXCHANGE" -> "return_handler";
            case "ORDER_STATUS" -> "order_query";
            default -> "knowledge_base";
        };

        log.info(">>> [路由节点] 意图: {} → 路由到: {}", intent, nextNode);

        // 把路由目标写入状态
        return Map.of(NEXT_NODE, nextNode);
    }
}
