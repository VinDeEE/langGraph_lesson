package com.example.streaming;

import com.example.common.AppConfig;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

/**
 * Web 流式输出 Demo
 *
 * 启动后访问: http://localhost:8080
 *
 * 技术方案：
 * - Java 内置 HttpServer
 * - SSE (Server-Sent Events) 实现流式推送
 * - 前端 EventSource 接收流式数据
 */
public class WebStreamingDemo {

    private static final Logger log = LoggerFactory.getLogger(WebStreamingDemo.class);
    private static StreamingChatLanguageModel model;

    public static void main(String[] args) throws Exception {
        // 加载配置
        Properties config = AppConfig.loadConfig();
        String apiKey = config.getProperty(AppConfig.OPENAI_API_KEY);
        String baseUrl = config.getProperty(AppConfig.OPENAI_API_BASE_URL);
        String modelName = config.getProperty(AppConfig.OPENAI_MODEL_NAME);

        if (!AppConfig.isApiKeyConfigured(apiKey)) {
            log.error("请先配置 API Key！");
            return;
        }

        // 初始化流式模型
        model = OpenAiStreamingChatModel.builder()
            .apiKey(apiKey)
            .baseUrl(baseUrl)
            .modelName(modelName)
            .timeout(Duration.ofSeconds(60))
            .build();

        // 创建 HTTP 服务器
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        // 首页 - HTML 页面
        server.createContext("/", exchange -> {
            String html = getPage();
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        // SSE 流式接口
        server.createContext("/stream", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            String message = "你好";
            if (query != null && query.startsWith("q=")) {
                message = java.net.URLDecoder.decode(query.substring(2), StandardCharsets.UTF_8);
            }

            // 设置 SSE 响应头
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            exchange.getResponseHeaders().set("Connection", "keep-alive");
            exchange.sendResponseHeaders(200, 0);

            OutputStream os = exchange.getResponseBody();
            String finalMessage = message;

            // 调用 LLM 流式 API
            CompletableFuture<Void> future = new CompletableFuture<>();

            model.generate(
                UserMessage.from(finalMessage),
                new StreamingResponseHandler() {
                    @Override
                    public void onNext(String token) {
                        try {
                            // SSE 格式: data: 内容\n\n
                            String sseData = "data: " + token.replace("\n", "\\n") + "\n\n";
                            os.write(sseData.getBytes(StandardCharsets.UTF_8));
                            os.flush();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onComplete(Response response) {
                        try {
                            // 发送完成标记
                            os.write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
                            os.flush();
                            os.close();
                            future.complete(null);
                        } catch (IOException e) {
                            future.completeExceptionally(e);
                        }
                    }

                    @Override
                    public void onError(Throwable error) {
                        try {
                            String errorData = "data: [ERROR] " + error.getMessage() + "\n\n";
                            os.write(errorData.getBytes(StandardCharsets.UTF_8));
                            os.flush();
                            os.close();
                            future.completeExceptionally(error);
                        } catch (IOException e) {
                            future.completeExceptionally(e);
                        }
                    }
                }
            );

            // 等待完成
            try {
                future.get();
            } catch (Exception e) {
                log.error("流式处理异常", e);
            }
        });

        server.start();
        System.out.println("\n" + "=".repeat(60));
        System.out.println("  LLM 流式输出 Web Demo");
        System.out.println("=".repeat(60));
        System.out.println("\n服务已启动！");
        System.out.println("访问: http://localhost:8080");
        System.out.println("\n按 Ctrl+C 退出\n");
    }

    private static String getPage() {
        return """
            <!DOCTYPE html>
            <html lang="zh">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>LLM 流式输出 Demo</title>
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                        min-height: 100vh;
                        display: flex;
                        justify-content: center;
                        align-items: center;
                        padding: 20px;
                    }
                    .container {
                        background: white;
                        border-radius: 16px;
                        box-shadow: 0 20px 60px rgba(0,0,0,0.3);
                        width: 100%;
                        max-width: 700px;
                        overflow: hidden;
                    }
                    .header {
                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                        color: white;
                        padding: 24px;
                        text-align: center;
                    }
                    .header h1 { font-size: 24px; margin-bottom: 8px; }
                    .header p { opacity: 0.9; font-size: 14px; }
                    .content { padding: 24px; }
                    .input-group {
                        display: flex;
                        gap: 12px;
                        margin-bottom: 24px;
                    }
                    input[type="text"] {
                        flex: 1;
                        padding: 14px 18px;
                        border: 2px solid #e0e0e0;
                        border-radius: 10px;
                        font-size: 16px;
                        transition: border-color 0.3s;
                    }
                    input[type="text"]:focus {
                        outline: none;
                        border-color: #667eea;
                    }
                    button {
                        padding: 14px 28px;
                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                        color: white;
                        border: none;
                        border-radius: 10px;
                        font-size: 16px;
                        cursor: pointer;
                        transition: transform 0.2s, box-shadow 0.2s;
                    }
                    button:hover {
                        transform: translateY(-2px);
                        box-shadow: 0 4px 15px rgba(102, 126, 234, 0.4);
                    }
                    button:disabled {
                        opacity: 0.6;
                        cursor: not-allowed;
                        transform: none;
                    }
                    .response-box {
                        background: #f8f9fa;
                        border-radius: 12px;
                        padding: 20px;
                        min-height: 150px;
                        max-height: 400px;
                        overflow-y: auto;
                        font-size: 15px;
                        line-height: 1.8;
                        color: #333;
                        white-space: pre-wrap;
                        word-break: break-word;
                    }
                    .response-box.active {
                        border-left: 4px solid #667eea;
                    }
                    .cursor {
                        display: inline-block;
                        width: 2px;
                        height: 18px;
                        background: #667eea;
                        animation: blink 0.8s infinite;
                        vertical-align: text-bottom;
                        margin-left: 2px;
                    }
                    @keyframes blink {
                        0%, 100% { opacity: 1; }
                        50% { opacity: 0; }
                    }
                    .status {
                        margin-top: 12px;
                        font-size: 13px;
                        color: #888;
                        text-align: center;
                        min-height: 20px;
                    }
                    .examples {
                        margin-top: 20px;
                        padding-top: 20px;
                        border-top: 1px solid #eee;
                    }
                    .examples p {
                        font-size: 13px;
                        color: #888;
                        margin-bottom: 10px;
                    }
                    .example-btn {
                        display: inline-block;
                        padding: 8px 14px;
                        margin: 4px;
                        background: #f0f0f0;
                        border: 1px solid #ddd;
                        border-radius: 20px;
                        font-size: 13px;
                        color: #555;
                        cursor: pointer;
                        transition: all 0.2s;
                    }
                    .example-btn:hover {
                        background: #667eea;
                        color: white;
                        border-color: #667eea;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1> LLM 流式输出 Demo</h1>
                        <p>体验 AI 逐 Token 实时生成效果</p>
                    </div>
                    <div class="content">
                        <div class="input-group">
                            <input type="text" id="query" placeholder="输入你的问题..." value="用一句话介绍什么是Java">
                            <button id="btn" onclick="startStream()">发送</button>
                        </div>
                        <div id="response" class="response-box"></div>
                        <div id="status" class="status"></div>
                        <div class="examples">
                            <p>试试这些问题：</p>
                            <span class="example-btn" onclick="setQuery(this)">什么是设计模式</span>
                            <span class="example-btn" onclick="setQuery(this)">用Python写一个快排</span>
                            <span class="example-btn" onclick="setQuery(this)">为什么天空是蓝色的</span>
                            <span class="example-btn" onclick="setQuery(this)">推荐一本编程书</span>
                        </div>
                    </div>
                </div>

                <script>
                    let isStreaming = false;

                    function setQuery(el) {
                        document.getElementById('query').value = el.textContent;
                        startStream();
                    }

                    function startStream() {
                        const query = document.getElementById('query').value.trim();
                        if (!query || isStreaming) return;

                        isStreaming = true;
                        const btn = document.getElementById('btn');
                        const response = document.getElementById('response');
                        const status = document.getElementById('status');

                        btn.disabled = true;
                        btn.textContent = '生成中...';
                        response.innerHTML = '<span class="cursor"></span>';
                        response.classList.add('active');
                        status.textContent = '正在连接...';

                        const eventSource = new EventSource('/stream?q=' + encodeURIComponent(query));
                        let fullText = '';

                        eventSource.onmessage = function(event) {
                            const data = event.data;

                            if (data === '[DONE]') {
                                eventSource.close();
                                isStreaming = false;
                                btn.disabled = false;
                                btn.textContent = '发送';
                                response.classList.remove('active');
                                status.textContent = '生成完成 ✓';
                                // 移除光标
                                const cursor = response.querySelector('.cursor');
                                if (cursor) cursor.remove();
                                return;
                            }

                            if (data.startsWith('[ERROR]')) {
                                eventSource.close();
                                isStreaming = false;
                                btn.disabled = false;
                                btn.textContent = '发送';
                                response.textContent = '错误: ' + data.substring(7);
                                response.classList.remove('active');
                                status.textContent = '生成失败';
                                return;
                            }

                            fullText += data;
                            status.textContent = '正在生成... (' + fullText.length + ' 字符)';

                            // 更新显示（保留光标）
                            const cursor = '<span class="cursor"></span>';
                            response.innerHTML = fullText.replace(/\\n/g, '<br>') + cursor;
                            response.scrollTop = response.scrollHeight;
                        };

                        eventSource.onerror = function() {
                            eventSource.close();
                            isStreaming = false;
                            btn.disabled = false;
                            btn.textContent = '发送';
                            if (!fullText) {
                                response.textContent = '连接失败，请检查服务是否启动';
                            }
                            response.classList.remove('active');
                            status.textContent = '连接中断';
                        };

                        // 回车发送
                        document.getElementById('query').onkeypress = function(e) {
                            if (e.key === 'Enter') startStream();
                        };
                    }
                </script>
            </body>
            </html>
            """;
    }
}
