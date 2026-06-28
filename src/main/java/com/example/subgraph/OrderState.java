package com.example.subgraph;

import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.*;

/**
 * 订单状态 - 父图和子图共享的状态
 *
 * 核心点：子图与父图共享同一个 State 类型
 * 这样子图可以读取和修改父图的状态
 */
public class OrderState extends AgentState {

    // 状态 key
    public static final String ORDER_ID = "orderId";
    public static final String USER_ID = "userId";
    public static final String AMOUNT = "amount";
    public static final String MESSAGES = "messages";

    // 子图相关
    public static final String VALIDATION_RESULT = "validationResult";
    public static final String PAYMENT_RESULT = "paymentResult";
    public static final String SHIPPING_RESULT = "shippingResult";
    public static final String CURRENT_STEP = "currentStep";

    public static final Map<String, Channel<?>> SCHEMA = Map.of(
        ORDER_ID, Channels.base(() -> ""),
        USER_ID, Channels.base(() -> ""),
        AMOUNT, Channels.base(() -> 0.0),
        MESSAGES, Channels.appender(ArrayList::new),
        VALIDATION_RESULT, Channels.base(() -> ""),
        PAYMENT_RESULT, Channels.base(() -> ""),
        SHIPPING_RESULT, Channels.base(() -> ""),
        CURRENT_STEP, Channels.base(() -> "")
    );

    public OrderState(Map<String, Object> initData) {
        super(initData);
    }

    public Optional<String> orderId() { return value(ORDER_ID); }
    public Optional<String> userId() { return value(USER_ID); }
    public Optional<Double> amount() { return value(AMOUNT); }
    public Optional<String> validationResult() { return value(VALIDATION_RESULT); }
    public Optional<String> paymentResult() { return value(PAYMENT_RESULT); }
    public Optional<String> shippingResult() { return value(SHIPPING_RESULT); }
    public Optional<String> currentStep() { return value(CURRENT_STEP); }
    public List<String> messages() { return this.<List<String>>value(MESSAGES).orElse(List.of()); }
}
