package com.example.customerservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 快递服务 - 模拟快递系统
 */
public class DeliveryService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryService.class);

    // 模拟快递数据
    private static final Map<String, Map<String, String>> DELIVERIES = Map.of(
        "SF1234567890", Map.of(
            "tracking", "SF1234567890",
            "status", "已签收",
            "location", "丰巢柜机A01",
            "pickupCode", "123456",
            "signedBy", "本人签收",
            "signedTime", "2024-01-15 14:30"
        ),
        "SF9876543210", Map.of(
            "tracking", "SF9876543210",
            "status", "派送中",
            "location", "快递员张师傅",
            "pickupCode", "",
            "signedBy", "",
            "signedTime", ""
        ),
        "SF1111111111", Map.of(
            "tracking", "SF1111111111",
            "status", "已丢失",
            "location", "未知",
            "pickupCode", "",
            "signedBy", "",
            "signedTime", ""
        )
    );

    /**
     * 查询快递信息
     */
    public Map<String, String> queryDelivery(String trackingNo) {
        log.info(">>> [快递服务] 查询快递: {}", trackingNo);
        return DELIVERIES.getOrDefault(trackingNo, Map.of("error", "快递不存在"));
    }

    /**
     * 检查是否在柜机
     */
    public boolean isInCabinet(String trackingNo) {
        log.info(">>> [快递服务] 检查柜机: {}", trackingNo);
        Map<String, String> delivery = DELIVERIES.get(trackingNo);
        return delivery != null && "丰巢柜机A01".equals(delivery.get("location"));
    }

    /**
     * 获取取件码
     */
    public String getPickupCode(String trackingNo) {
        log.info(">>> [快递服务] 获取取件码: {}", trackingNo);
        Map<String, String> delivery = DELIVERIES.get(trackingNo);
        return delivery != null ? delivery.getOrDefault("pickupCode", "") : "";
    }

    /**
     * 创建丢件工单
     */
    public String createLostTicket(String trackingNo) {
        log.info(">>> [快递服务] 创建丢件工单: {}", trackingNo);
        return "TK" + System.currentTimeMillis();
    }
}
