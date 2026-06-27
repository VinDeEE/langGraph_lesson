package com.example.assistant;

import dev.langchain4j.model.openai.OpenAiChatModel;
import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 工具执行 Agent
 *
 * 价值体现：动态决定调用哪些工具、用什么参数
 * - 不是硬编码的 if-else，而是 LLM 根据解析结果动态决策
 * - 可以组合多个工具完成复杂任务
 */
public class ToolExecutionAgent implements NodeAction<TaskAssistantState> {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutionAgent.class);
    private final OpenAiChatModel model;

    // 模拟的工具集
    private static final String TOOLS_DESCRIPTION = """
        可用工具：
        1. queryCalendar(date) - 查询某天的日程安排
        2. bookRoom(time, duration, attendees) - 预订会议室
        3. createTask(taskName, deadline, priority) - 创建待办任务
        4. sendNotification(to, message) - 发送通知
        5. searchHistory(keyword) - 搜索历史记录
        6. setReminder(time, content) - 设置提醒
        """;

    public ToolExecutionAgent(OpenAiChatModel model) {
        this.model = model;
    }

    @Override
    public Map<String, Object> apply(TaskAssistantState state) throws Exception {
        String parsedTask = state.parsedTask().orElse("{}");
        log.info(">>> [工具执行Agent] 根据解析结果执行工具");

        String prompt = """
            你是一个工具执行专家。根据任务解析结果，决定调用哪些工具。

            任务解析结果:
            %s

            %s

            请输出要执行的工具调用（JSON 数组格式，不要输出其他内容）：
            [
              {
                "tool": "工具名",
                "params": {"参数名": "参数值"},
                "reason": "调用原因"
              }
            ]

            注意：
            1. 可以同时调用多个工具（如查日程+订会议室）
            2. 参数要从解析结果中提取
            3. 如果缺少必要参数，不调用该工具
            """.formatted(parsedTask, TOOLS_DESCRIPTION);

        String toolCalls = model.chat(prompt);
        log.info(">>> [工具执行Agent] 工具调用计划: {}", toolCalls);

        // 模拟工具执行结果
        String simulatedResult = simulateToolExecution(toolCalls);

        return Map.of(
            TaskAssistantState.TOOL_RESULTS, simulatedResult,
            TaskAssistantState.MESSAGES, "【工具执行】已完成"
        );
    }

    /**
     * 模拟工具执行（实际项目中会真正调用工具）
     */
    private String simulateToolExecution(String toolCalls) {
        StringBuilder result = new StringBuilder();

        if (toolCalls.contains("queryCalendar")) {
            result.append("[日历查询结果] 明天下午2点有产品评审会议，3点有客户电话\n");
        }
        if (toolCalls.contains("bookRoom")) {
            result.append("[会议室预订] 已预订A301会议室，明天下午4点-5点，可容纳8人\n");
        }
        if (toolCalls.contains("createTask")) {
            result.append("[任务创建] 已创建待办：准备会议材料，截止时间明天中午12点\n");
        }
        if (toolCalls.contains("sendNotification")) {
            result.append("[通知发送] 已发送会议通知给相关参与者\n");
        }
        if (toolCalls.contains("setReminder")) {
            result.append("[提醒设置] 已设置明天上午10点提醒准备会议\n");
        }

        return result.length() > 0 ? result.toString() : "[无工具调用]";
    }
}
