package com.example.customerservice;

import com.example.common.AppConfig;
import com.example.customerservice.agent.CustomerServiceAgent;
import com.example.customerservice.hook.ExecutionTracer;
import com.example.customerservice.service.*;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * 服务容器 - 模拟 Spring IoC 容器
 *
 * 负责创建和管理所有服务实例
 */
public class ServiceContainer {

    private static final Logger log = LoggerFactory.getLogger(ServiceContainer.class);

    private final ChatLanguageModel chatModel;
    private final OrderService orderService;
    private final DeliveryService deliveryService;
    private final ComplaintService complaintService;
    private final WorkOrderService workOrderService;
    private final CustomerServiceAgent agent;
    private final ExecutionTracer tracer;

    public ServiceContainer() {
        // 加载配置
        java.util.Properties config;
        try {
            config = AppConfig.loadConfig();
        } catch (Exception e) {
            config = new java.util.Properties();
            log.error("加载配置失败", e);
        }
        String apiKey = config.getProperty(AppConfig.OPENAI_API_KEY);
        String baseUrl = config.getProperty(AppConfig.OPENAI_API_BASE_URL);
        String modelName = config.getProperty(AppConfig.OPENAI_MODEL_NAME);

        // 初始化 LLM
        this.chatModel = OpenAiChatModel.builder()
            .apiKey(apiKey)
            .baseUrl(baseUrl)
            .modelName(modelName)
            .timeout(Duration.ofSeconds(60))
            .build();

        // 初始化服务
        this.orderService = new OrderService();
        this.deliveryService = new DeliveryService();
        this.complaintService = new ComplaintService();
        this.workOrderService = new WorkOrderService();
        this.tracer = new ExecutionTracer();

        // 初始化 Agent
        this.agent = new CustomerServiceAgent(
            chatModel, orderService, deliveryService,
            complaintService, workOrderService, tracer
        );

        log.info("服务容器初始化完成");
    }

    public ChatLanguageModel getChatModel() { return chatModel; }
    public OrderService getOrderService() { return orderService; }
    public DeliveryService getDeliveryService() { return deliveryService; }
    public ComplaintService getComplaintService() { return complaintService; }
    public WorkOrderService getWorkOrderService() { return workOrderService; }
    public CustomerServiceAgent getAgent() { return agent; }
    public ExecutionTracer getTracer() { return tracer; }
}
