package com.example.customerservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 工单服务 - 管理工单创建和流转
 */
public class WorkOrderService {

    private static final Logger log = LoggerFactory.getLogger(WorkOrderService.class);

    // 工单类型
    public static final String TYPE_LOST_PACKAGE = "丢件";
    public static final String TYPE_OVERCHARGE = "多收费";
    public static final String TYPE_DAMAGED = "损坏";
    public static final String TYPE_DELAY = "延迟";

    /**
     * 创建工单
     */
    public Map<String, String> createWorkOrder(String type, String userId, String description,
                                                String trackingNo, String handler) {
        log.info(">>> [工单服务] 创建工单: type={}, userId={}, handler={}", type, userId, handler);
        String orderId = "WO" + System.currentTimeMillis();

        Map<String, String> workOrder = new HashMap<>();
        workOrder.put("workOrderId", orderId);
        workOrder.put("type", type);
        workOrder.put("userId", userId);
        workOrder.put("description", description);
        workOrder.put("trackingNo", trackingNo);
        workOrder.put("handler", handler);
        workOrder.put("status", "待处理");
        workOrder.put("createdAt", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
            .format(new java.util.Date()));

        return workOrder;
    }

    /**
     * 查询工单状态
     */
    public Map<String, String> queryWorkOrder(String workOrderId) {
        log.info(">>> [工单服务] 查询工单: {}", workOrderId);
        return Map.of(
            "workOrderId", workOrderId,
            "status", "处理中",
            "handler", "客服主管",
            "createdAt", "2024-01-15 10:00"
        );
    }

    /**
     * 根据类型获取处理人
     */
    public String getHandlerByType(String type) {
        log.info(">>> [工单服务] 获取处理人: {}", type);
        return switch (type) {
            case TYPE_LOST_PACKAGE -> "物流部赵经理";
            case TYPE_OVERCHARGE -> "财务部王经理";
            case TYPE_DAMAGED -> "品控部李经理";
            case TYPE_DELAY -> "物流部赵经理";
            default -> "客服主管";
        };
    }
}
