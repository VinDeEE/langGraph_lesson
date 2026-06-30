package com.example.customerservice;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 聊天 Controller - 处理 HTTP 请求
 *
 * 职责：
 * 1. 接收用户消息
 * 2. 调用 Agent 处理
 * 3. 返回响应
 */
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private final ServiceContainer container;
    private final Map<String, StringBuilder> chatHistories = new ConcurrentHashMap<>();

    public ChatController(ServiceContainer container) {
        this.container = container;
    }

    /**
     * 服务页面
     */
    public void servePage(HttpExchange exchange) throws IOException {
        String html = getHtmlPage();
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    /**
     * 处理聊天请求
     */
    public void chat(HttpExchange exchange) throws IOException {
        // 读取请求体
        String requestBody = readRequestBody(exchange);
        log.info("收到请求: {}", requestBody);

        // 解析请求（简单解析）
        String userId = extractParam(requestBody, "userId");
        String message = extractParam(requestBody, "message");

        // 调用 Agent 处理
        String response;
        try {
            response = container.getAgent().chat(userId, message);
        } catch (Exception e) {
            log.error("处理失败", e);
            response = "抱歉，处理您的请求时出现错误，请稍后重试。";
        }

        // 记录历史
        chatHistories.computeIfAbsent(userId, k -> new StringBuilder())
            .append("用户: ").append(message).append("\n")
            .append("助手: ").append(response).append("\n\n");

        // 返回响应
        String jsonResponse = String.format(
            "{\"userId\": \"%s\", \"message\": \"%s\", \"response\": \"%s\"}",
            userId, message, response.replace("\"", "\\\"").replace("\n", "\\n")
        );

        byte[] bytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    /**
     * 获取历史记录
     */
    public void getHistory(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        String userId = extractParam(query != null ? query : "", "userId");

        String history = chatHistories.getOrDefault(userId, new StringBuilder()).toString();
        String json = String.format("{\"userId\": \"%s\", \"history\": \"%s\"}",
            userId, history.replace("\"", "\\\"").replace("\n", "\\n"));

        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String readRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String extractParam(String body, String param) {
        String key = "\"" + param + "\":\"";
        int start = body.indexOf(key);
        if (start < 0) return "";
        start += key.length();
        int end = body.indexOf("\"", start);
        return end > start ? body.substring(start, end) : "";
    }

    private String getHtmlPage() {
        return """
            <!DOCTYPE html>
            <html lang="zh">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>智能客服系统</title>
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    body { font-family: -apple-system, BlinkMacSystemFont, sans-serif; background: #f0f2f5; }
                    .container { max-width: 800px; margin: 0 auto; padding: 20px; }
                    .header { background: linear-gradient(135deg, #667eea, #764ba2); color: white;
                              padding: 30px; border-radius: 16px; margin-bottom: 20px; text-align: center; }
                    .chat-box { background: white; border-radius: 16px; box-shadow: 0 2px 10px rgba(0,0,0,0.1);
                                padding: 24px; margin-bottom: 20px; min-height: 400px; max-height: 600px;
                                overflow-y: auto; }
                    .message { margin-bottom: 16px; display: flex; }
                    .message.user { justify-content: flex-end; }
                    .message .bubble { max-width: 70%; padding: 12px 16px; border-radius: 12px;
                                       font-size: 14px; line-height: 1.6; }
                    .message.user .bubble { background: #667eea; color: white; border-bottom-right-radius: 4px; }
                    .message.bot .bubble { background: #f0f2f5; color: #333; border-bottom-left-radius: 4px; }
                    .input-area { display: flex; gap: 12px; }
                    .input-area input { flex: 1; padding: 14px; border: 2px solid #e0e0e0; border-radius: 10px;
                                        font-size: 14px; outline: none; }
                    .input-area input:focus { border-color: #667eea; }
                    .input-area button { padding: 14px 24px; background: #667eea; color: white; border: none;
                                         border-radius: 10px; cursor: pointer; font-size: 14px; }
                    .input-area button:hover { background: #5a6fd6; }
                    .input-area button:disabled { background: #ccc; }
                    .examples { margin-top: 16px; }
                    .examples span { display: inline-block; padding: 8px 12px; background: #f0f2f5;
                                     border-radius: 16px; font-size: 12px; margin: 4px; cursor: pointer; }
                    .examples span:hover { background: #e0e0e0; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1> 智能客服系统</h1>
                        <p>支持订单查询、丢件反馈、投诉处理</p>
                    </div>
                    <div class="chat-box" id="chatBox"></div>
                    <div class="input-area">
                        <input type="text" id="userInput" placeholder="请输入您的问题..."
                               onkeypress="if(event.key==='Enter')sendMessage()">
                        <button onclick="sendMessage()" id="sendBtn">发送</button>
                    </div>
                    <div class="examples">
                        <span onclick="setQuery('查询我的订单，手机号13800138000，姓名张三')">查询订单</span>
                        <span onclick="setQuery('我的快递丢了，单号SF1234567890')">反馈丢件</span>
                        <span onclick="setQuery('我被多收费了，订单号ORD001')">投诉多收费</span>
                    </div>
                </div>
                <script>
                    const userId = 'user_' + Math.random().toString(36).substr(2, 9);

                    function setQuery(text) {
                        document.getElementById('userInput').value = text;
                        sendMessage();
                    }

                    async function sendMessage() {
                        const input = document.getElementById('userInput');
                        const message = input.value.trim();
                        if (!message) return;

                        addMessage('user', message);
                        input.value = '';

                        const btn = document.getElementById('sendBtn');
                        btn.disabled = true;
                        btn.textContent = '处理中...';

                        try {
                            const response = await fetch('/api/chat', {
                                method: 'POST',
                                headers: { 'Content-Type': 'application/json' },
                                body: JSON.stringify({ userId, message })
                            });
                            const data = await response.json();
                            addMessage('bot', data.response);
                        } catch (error) {
                            addMessage('bot', '抱歉，处理失败，请稍后重试。');
                        }

                        btn.disabled = false;
                        btn.textContent = '发送';
                    }

                    function addMessage(type, text) {
                        const chatBox = document.getElementById('chatBox');
                        const div = document.createElement('div');
                        div.className = 'message ' + type;
                        div.innerHTML = '<div class="bubble">' + text.replace(/\\n/g, '<br>') + '</div>';
                        chatBox.appendChild(div);
                        chatBox.scrollTop = chatBox.scrollHeight;
                    }
                </script>
            </body>
            </html>
            """;
    }
}
