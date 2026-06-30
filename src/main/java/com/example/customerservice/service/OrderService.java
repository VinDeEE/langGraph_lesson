package com.example.customerservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 订单服务 - 模拟订单系统
 */
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    // 模拟订单数据
    private static final Map<String, List<Map<String, String>>> USER_ORDERS = Map.of(
        "13800138000", List.of(
            Map.of("orderId", "ORD001", "status", "已发货", "amount", "299.00",
                   "item", "无线耳机", "tracking", "SF1234567890"),
            Map.of("orderId", "ORD002", "status", "已完成", "amount", "1599.00",
                   "item", "机械键盘", "tracking", "SF9876543210")
        ),
        "13900139000", List.of(
            Map.of("orderId", "ORD003", "status", "待发货", "amount", "59.00",
                   "item", "数据线", "tracking", "")
        )
    );

    /**
     * 验证用户信息
     */
    public boolean validateUser(String phone, String name) {
        log.info(">>> [订单服务] 验证用户: {} {}", phone, name);
        // 模拟验证逻辑
        return phone != null && phone.length() == 11 && name != null && !name.isEmpty();
    }

    /**
     * 根据手机号查询订单列表
     */
    public List<Map<String, String>> queryOrdersByPhone(String phone) {
        log.info(">>> [订单服务] 查询订单: {}", phone);
        return USER_ORDERS.getOrDefault(phone, List.of());
    }

    /**
     * 查询订单详情
     */
    public Map<String, String> queryOrderDetail(String orderId) {
        log.info(">>> [订单服务] 查询订单详情: {}", orderId);
        return USER_ORDERS.values().stream()
            .flatMap(List::stream)
            .filter(order -> order.get("orderId").equals(orderId))
            .findFirst()
            .orElse(Map.of("error", "订单不存在"));
    }

    /**
     * 查询订单费用明细
     */
    public Map<String, String> queryOrderCharges(String orderId) {
        log.info(">>> [订单服务] 查询费用明细: {}", orderId);
        return Map.of(
            "orderId", orderId,
            "itemPrice", "259.00",
            "shippingFee", "10.00",
            "discount", "-20.00",
            "totalAmount", "249.00",
            "paymentAmount", "299.00",
            "overCharge", "50.00"
        );
    }
}
