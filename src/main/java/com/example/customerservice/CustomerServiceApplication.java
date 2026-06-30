package com.example.customerservice;

import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

/**
 * 客服系统应用入口
 *
 * 启动后访问: http://localhost:8080
 */
public class CustomerServiceApplication {

    private static final Logger log = LoggerFactory.getLogger(CustomerServiceApplication.class);

    public static void main(String[] args) throws Exception {
        // 初始化服务
        ServiceContainer container = new ServiceContainer();

        // 创建 HTTP 服务器
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        // 注册 Controller
        ChatController chatController = new ChatController(container);
        server.createContext("/", chatController::servePage);
        server.createContext("/api/chat", chatController::chat);
        server.createContext("/api/history", chatController::getHistory);

        server.start();
        log.info("客服系统启动成功，访问: http://localhost:8080");
        System.out.println("\n" + "=".repeat(60));
        System.out.println("  智能客服系统已启动");
        System.out.println("  访问: http://localhost:8080");
        System.out.println("=".repeat(60));
    }
}
