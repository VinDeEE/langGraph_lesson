package com.example.visualization;

import com.sun.net.httpserver.HttpServer;
import org.bsc.langgraph4j.*;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import org.bsc.langgraph4j.state.StateSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;

/**
 * 图可视化 Demo
 *
 * 功能：
 * 1. 展示图结构（Mermaid 图表）
 * 2. 展示执行状态
 * 3. 展示检查点历史
 * 4. 模拟执行流程
 *
 * 访问: http://localhost:8080
 */
public class GraphVisualizationDemo {

    private static final Logger log = LoggerFactory.getLogger(GraphVisualizationDemo.class);
    private static CompiledGraph<OrderState> graph;
    private static MemorySaver saver;

    // 状态定义
    static class OrderState extends AgentState {
        public static final String ORDER_ID = "orderId";
        public static final String STATUS = "status";
        public static final String CURRENT_STEP = "currentStep";
        public static final String MESSAGES = "messages";

        public static final Map<String, Channel<?>> SCHEMA = Map.of(
            ORDER_ID, Channels.base(() -> ""),
            STATUS, Channels.base(() -> ""),
            CURRENT_STEP, Channels.base(() -> ""),
            MESSAGES, Channels.appender(ArrayList::new)
        );

        public OrderState(Map<String, Object> initData) { super(initData); }
        public Optional<String> orderId() { return value(ORDER_ID); }
        public Optional<String> status() { return value(STATUS); }
        public Optional<String> currentStep() { return value(CURRENT_STEP); }
        public List<String> messages() { return this.<List<String>>value(MESSAGES).orElse(List.of()); }
    }

    public static void main(String[] args) throws Exception {
        // 创建图
        saver = new MemorySaver();

        StateGraph<OrderState> stateGraph = new StateGraph<>(
            OrderState.SCHEMA,
            OrderState::new
        )
            .addNode("receive_order", node_async(state -> {
                log.info(">>> [接收订单]");
                return Map.of(
                    OrderState.CURRENT_STEP, "receive_order",
                    OrderState.STATUS, "已接收",
                    OrderState.MESSAGES, "【接收订单】订单已接收"
                );
            }))
            .addNode("validate", node_async(state -> {
                log.info(">>> [验证]");
                return Map.of(
                    OrderState.CURRENT_STEP, "validate",
                    OrderState.STATUS, "验证中",
                    OrderState.MESSAGES, "【验证】订单信息验证通过"
                );
            }))
            .addNode("check_inventory", node_async(state -> {
                log.info(">>> [检查库存]");
                return Map.of(
                    OrderState.CURRENT_STEP, "check_inventory",
                    OrderState.STATUS, "库存检查中",
                    OrderState.MESSAGES, "【库存】库存充足"
                );
            }))
            .addNode("process_payment", node_async(state -> {
                log.info(">>> [处理支付]");
                return Map.of(
                    OrderState.CURRENT_STEP, "process_payment",
                    OrderState.STATUS, "支付处理中",
                    OrderState.MESSAGES, "【支付】支付成功"
                );
            }))
            .addNode("ship_order", node_async(state -> {
                log.info(">>> [发货]");
                return Map.of(
                    OrderState.CURRENT_STEP, "ship_order",
                    OrderState.STATUS, "已发货",
                    OrderState.MESSAGES, "【发货】商品已发出"
                );
            }))
            .addNode("complete", node_async(state -> {
                log.info(">>> [完成]");
                return Map.of(
                    OrderState.CURRENT_STEP, "complete",
                    OrderState.STATUS, "已完成",
                    OrderState.MESSAGES, "【完成】订单处理完成"
                );
            }))
            .addEdge(START, "receive_order")
            .addEdge("receive_order", "validate")
            .addEdge("validate", "check_inventory")
            .addEdge("check_inventory", "process_payment")
            .addEdge("process_payment", "ship_order")
            .addEdge("ship_order", "complete")
            .addEdge("complete", END);

        CompileConfig config = CompileConfig.builder()
            .checkpointSaver(saver)
            .build();

        graph = stateGraph.compile(config);

        // 生成 Mermaid 图表
        String mermaid = graph.getGraph(GraphRepresentation.Type.MERMAID, "订单处理流程", false).content();
        System.out.println("Mermaid 图表:\n" + mermaid);

        // 启动 Web 服务器
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        // 首页
        server.createContext("/", exchange -> {
            log.info("收到请求: {}", exchange.getRequestURI());
            try {
                String html = getPage(mermaid);
                byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
                log.info("响应已发送");
            } catch (Exception e) {
                log.error("处理请求异常", e);
                exchange.sendResponseHeaders(500, 0);
            }
        });

        // 执行订单 API
        server.createContext("/api/execute", exchange -> {
            String orderId = "ORD-" + System.currentTimeMillis();
            RunnableConfig runConfig = RunnableConfig.builder().threadId(orderId).build();

            Map<String, Object> inputs = Map.of(
                OrderState.ORDER_ID, orderId
            );

            var result = graph.invoke(inputs, runConfig);

            StringBuilder response = new StringBuilder();
            response.append("{\"orderId\":\"").append(orderId).append("\",");
            response.append("\"steps\":[");

            result.ifPresent(state -> {
                List<String> messages = state.messages();
                for (int i = 0; i < messages.size(); i++) {
                    if (i > 0) response.append(",");
                    response.append("\"").append(messages.get(i).replace("\"", "\\\"")).append("\"");
                }
            });

            response.append("],");
            response.append("\"status\":\"").append(result.map(s -> s.status().orElse("")).orElse("")).append("\"");
            response.append("}");

            byte[] bytes = response.toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        server.start();
        System.out.println("\n" + "=".repeat(60));
        System.out.println("  图可视化 Demo");
        System.out.println("=".repeat(60));
        System.out.println("\n服务已启动！");
        System.out.println("访问: http://localhost:8080");
        System.out.println("\n按 Ctrl+C 退出\n");
    }

    private static String getPage(String mermaid) {
        // 使用字符串拼接而不是 formatted，避免 % 字符问题
        return "<!DOCTYPE html>\n" +
            "<html lang=\"zh\">\n" +
            "<head>\n" +
            "    <meta charset=\"UTF-8\">\n" +
            "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
            "    <title>LangGraph4j 可视化</title>\n" +
            "    <script src=\"https://cdn.jsdelivr.net/npm/mermaid/dist/mermaid.min.js\"></script>\n" +
            "    <style>\n" +
            "        * { margin: 0; padding: 0; box-sizing: border-box; }\n" +
            "        body {\n" +
            "            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;\n" +
            "            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);\n" +
            "            min-height: 100vh;\n" +
            "            padding: 20px;\n" +
            "        }\n" +
            "        .container { max-width: 1200px; margin: 0 auto; }\n" +
            "        h1 { text-align: center; color: white; margin-bottom: 30px; text-shadow: 2px 2px 4px rgba(0,0,0,0.3); }\n" +
            "        .card {\n" +
            "            background: white;\n" +
            "            border-radius: 16px;\n" +
            "            box-shadow: 0 10px 40px rgba(0,0,0,0.2);\n" +
            "            padding: 30px;\n" +
            "            margin-bottom: 24px;\n" +
            "        }\n" +
            "        .card h2 { color: #667eea; margin-bottom: 20px; font-size: 20px; }\n" +
            "        .mermaid { display: flex; justify-content: center; padding: 30px; background: #f8f9fa; border-radius: 12px; }\n" +
            "        .btn {\n" +
            "            display: inline-block;\n" +
            "            padding: 14px 28px;\n" +
            "            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);\n" +
            "            color: white;\n" +
            "            border: none;\n" +
            "            border-radius: 10px;\n" +
            "            font-size: 16px;\n" +
            "            cursor: pointer;\n" +
            "            transition: all 0.3s;\n" +
            "        }\n" +
            "        .btn:hover { transform: translateY(-3px); box-shadow: 0 6px 20px rgba(102,126,234,0.4); }\n" +
            "        .btn:disabled { opacity: 0.6; cursor: not-allowed; transform: none; }\n" +
            "        .result {\n" +
            "            margin-top: 20px;\n" +
            "            padding: 20px;\n" +
            "            background: #f8f9fa;\n" +
            "            border-radius: 12px;\n" +
            "            font-family: 'Consolas', monospace;\n" +
            "            white-space: pre-wrap;\n" +
            "            max-height: 400px;\n" +
            "            overflow-y: auto;\n" +
            "            display: none;\n" +
            "            border-left: 4px solid #667eea;\n" +
            "        }\n" +
            "        .timeline { margin-top: 20px; }\n" +
            "        .timeline-item {\n" +
            "            display: flex;\n" +
            "            align-items: center;\n" +
            "            padding: 14px 18px;\n" +
            "            border-left: 3px solid #667eea;\n" +
            "            margin-left: 24px;\n" +
            "            margin-bottom: 10px;\n" +
            "            background: #f8f9fa;\n" +
            "            border-radius: 0 10px 10px 0;\n" +
            "            animation: slideIn 0.3s ease-out;\n" +
            "        }\n" +
            "        @keyframes slideIn { from { opacity: 0; transform: translateX(-20px); } to { opacity: 1; transform: translateX(0); } }\n" +
            "        .timeline-item::before {\n" +
            "            content: '';\n" +
            "            width: 14px; height: 14px;\n" +
            "            background: #667eea;\n" +
            "            border-radius: 50%;\n" +
            "            margin-left: -29px;\n" +
            "            margin-right: 16px;\n" +
            "            flex-shrink: 0;\n" +
            "            box-shadow: 0 0 0 4px rgba(102,126,234,0.2);\n" +
            "        }\n" +
            "        .info-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 16px; margin-top: 20px; }\n" +
            "        .info-item { padding: 20px; background: linear-gradient(135deg, #667eea15 0%, #764ba215 100%); border-radius: 12px; text-align: center; }\n" +
            "        .info-item .label { font-size: 13px; color: #888; margin-bottom: 10px; }\n" +
            "        .info-item .value { font-size: 28px; font-weight: bold; color: #667eea; }\n" +
            "    </style>\n" +
            "</head>\n" +
            "<body>\n" +
            "    <div class=\"container\">\n" +
            "        <h1> LangGraph4j 图可视化 Demo</h1>\n" +
            "\n" +
            "        <div class=\"card\">\n" +
            "            <h2> 图结构（Mermaid 流程图）</h2>\n" +
            "            <div class=\"mermaid\">\n" +
            mermaid + "\n" +
            "            </div>\n" +
            "        </div>\n" +
            "\n" +
            "        <div class=\"card\">\n" +
            "            <h2>⚡ 执行测试</h2>\n" +
            "            <p style=\"margin-bottom: 20px; color: #666; line-height: 1.6;\">\n" +
            "                点击按钮模拟执行一个订单处理流程，观察每个节点的执行状态和时间线。\n" +
            "            </p>\n" +
            "            <button class=\"btn\" id=\"executeBtn\" onclick=\"executeGraph()\"> 执行订单处理流程</button>\n" +
            "            <div id=\"result\" class=\"result\"></div>\n" +
            "            <div id=\"timeline\" class=\"timeline\"></div>\n" +
            "        </div>\n" +
            "\n" +
            "        <div class=\"card\">\n" +
            "            <h2> 图信息</h2>\n" +
            "            <div class=\"info-grid\">\n" +
            "                <div class=\"info-item\"><div class=\"label\">节点数量</div><div class=\"value\">8</div></div>\n" +
            "                <div class=\"info-item\"><div class=\"label\">边数量</div><div class=\"value\">8</div></div>\n" +
            "                <div class=\"info-item\"><div class=\"label\">图类型</div><div class=\"value\">有向图</div></div>\n" +
            "                <div class=\"info-item\"><div class=\"label\">检查点</div><div class=\"value\">已启用</div></div>\n" +
            "            </div>\n" +
            "        </div>\n" +
            "    </div>\n" +
            "\n" +
            "    <script>\n" +
            "        mermaid.initialize({ startOnLoad: true, theme: 'default' });\n" +
            "\n" +
            "        async function executeGraph() {\n" +
            "            const btn = document.getElementById('executeBtn');\n" +
            "            const result = document.getElementById('result');\n" +
            "            const timeline = document.getElementById('timeline');\n" +
            "\n" +
            "            btn.disabled = true;\n" +
            "            btn.textContent = '执行中...';\n" +
            "            result.style.display = 'block';\n" +
            "            result.textContent = '开始执行...\\n';\n" +
            "            timeline.innerHTML = '';\n" +
            "\n" +
            "            try {\n" +
            "                const response = await fetch('/api/execute');\n" +
            "                const data = await response.json();\n" +
            "\n" +
            "                result.textContent = '执行完成！\\n\\n';\n" +
            "                result.textContent += '订单ID: ' + data.orderId + '\\n';\n" +
            "                result.textContent += '最终状态: ' + data.status + '\\n\\n';\n" +
            "                result.textContent += '执行日志:\\n';\n" +
            "                data.steps.forEach(step => { result.textContent += '  ' + step + '\\n'; });\n" +
            "\n" +
            "                data.steps.forEach((step, index) => {\n" +
            "                    const item = document.createElement('div');\n" +
            "                    item.className = 'timeline-item';\n" +
            "                    item.textContent = step;\n" +
            "                    item.style.animationDelay = (index * 0.1) + 's';\n" +
            "                    timeline.appendChild(item);\n" +
            "                });\n" +
            "            } catch (error) {\n" +
            "                result.textContent = '执行失败: ' + error.message;\n" +
            "            }\n" +
            "\n" +
            "            btn.disabled = false;\n" +
            "            btn.textContent = ' 执行订单处理流程';\n" +
            "        }\n" +
            "    </script>\n" +
            "</body>\n" +
            "</html>";
    }
}
