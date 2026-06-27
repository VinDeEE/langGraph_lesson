package com.example.assistant;

import dev.langchain4j.model.openai.OpenAiChatModel;
import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 任务解析 Agent
 *
 * 价值体现：理解模糊的自然语言输入
 * - "明天有个会" → 提取时间、推断需要查日历
 * - "帮我订个会议室" → 需要追问时间、人数
 * - "那个事情取消了" → 需要上下文理解"那个"是什么
 */
public class TaskParsingAgent implements NodeAction<TaskAssistantState> {

    private static final Logger log = LoggerFactory.getLogger(TaskParsingAgent.class);
    private final OpenAiChatModel model;

    public TaskParsingAgent(OpenAiChatModel model) {
        this.model = model;
    }

    @Override
    public Map<String, Object> apply(TaskAssistantState state) throws Exception {
        String userInput = state.userInput().orElse("");
        log.info(">>> [任务解析Agent] 解析用户输入: {}", userInput);

        String prompt = """
            你是一个任务解析专家。分析用户的自然语言输入，提取结构化信息。

            用户输入: %s

            请输出 JSON 格式的解析结果（不要输出其他内容）：
            {
              "intent": "任务意图（如：创建任务、查询日程、预订会议室、取消任务、提醒设置等）",
              "taskName": "任务名称（如有）",
              "time": "时间信息（如有，格式化为可理解的描述）",
              "location": "地点信息（如有）",
              "participants": "参与者（如有）",
              "priority": "优先级（高/中/低，根据上下文推断）",
              "needClarify": false,
              "clarifyQuestion": "如果信息不完整需要追问，这里写追问问题"
            }

            注意：
            1. 如果信息完整，needClarify 设为 false
            2. 如果缺少关键信息（如订会议室但没说时间），needClarify 设为 true，并在 clarifyQuestion 中写追问
            3. 根据上下文推断隐含信息（如"明天下午开会"暗示需要会议室）
            """.formatted(userInput);

        String result = model.chat(prompt);
        log.info(">>> [任务解析Agent] 解析结果: {}", result);

        // 判断是否需要追问
        boolean needClarify = result.contains("\"needClarify\": true");
        String nextAction = needClarify ? "clarify" : "execute";

        return Map.of(
            TaskAssistantState.PARSED_TASK, result,
            TaskAssistantState.NEXT_ACTION, nextAction,
            TaskAssistantState.MESSAGES, "【任务解析】" + (needClarify ? "需要追问" : "信息完整")
        );
    }
}
