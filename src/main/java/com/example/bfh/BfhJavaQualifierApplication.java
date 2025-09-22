package com.example.bfh;

import com.example.bfh.service.WebhookService;
import com.example.bfh.model.GenerateResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BfhJavaQualifierApplication implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(BfhJavaQualifierApplication.class);
    private final WebhookService webhookService;

    @Value("${app.name}")  private String name;
    @Value("${app.regNo}") private String regNo;
    @Value("${app.email}") private String email;
    @Value("${app.fallback.testWebhookUrl}") private String fallbackTestWebhookUrl;

    public BfhJavaQualifierApplication(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    public static void main(String[] args) {
        SpringApplication.run(BfhJavaQualifierApplication.class, args);
    }

    @Override
    public void run(String... args) {
        log.info("=== BFH Qualifier App Starting ===");
        try {
            // 1) Generate webhook + token
            GenerateResponse resp = webhookService.generateWebhook(name, regNo, email);
            String webhookUrl = (resp.getWebhook() != null && !resp.getWebhook().isBlank())
                    ? resp.getWebhook() : fallbackTestWebhookUrl;
            String accessToken = resp.getAccessToken();

            log.info("Webhook URL: {}", webhookUrl);
            log.info("Access Token received: {}", (accessToken == null ? "null" : "***"));

            // 2) Which question applies?
            String link = webhookService.questionLinkForRegNo(regNo);
            log.info("Your SQL Question link: {}", link);

            // 3) Load SQL
            String finalQuery = webhookService.loadSqlFromFile("solution.sql");
            if (finalQuery == null || finalQuery.isBlank()) {
                log.warn("No SQL found in solution.sql. Paste your final query and re-run.");
                return;
            }

            // 4) Submit
            webhookService.submitFinalQuery(webhookUrl, accessToken, finalQuery);
            log.info("Submitted final SQL successfully.");
        } catch (Exception e) {
            log.error("Fatal error", e);
        }
        log.info("=== Finished ===");
    }
}
