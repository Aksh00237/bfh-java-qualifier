package com.example.bfh.service;

import com.example.bfh.model.GenerateResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Service
public class WebhookService {
    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper mapper = new ObjectMapper();

    private static final String GENERATE_URL = "https://bfhldevapigw.healthrx.co.in/hiring/generateWebhook/JAVA";
    private static final String QUESTION_ODD  = "https://drive.google.com/file/d/1IeSI6l6KoSQAFfRihIT9tEDICtoz-G_/view?usp=sharing";
    private static final String QUESTION_EVEN = "https://drive.google.com/file/d/143MR5cLFrlNEuHzzWJ5RHnEW_uijuM9X/view?usp=sharing";

    public WebhookService(RestTemplate restTemplate) { this.restTemplate = restTemplate; }

    public GenerateResponse generateWebhook(String name, String regNo, String email) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("name", name);
            body.put("regNo", regNo);
            body.put("email", email);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<String> resp = restTemplate.postForEntity(GENERATE_URL, entity, String.class);
            log.info("Generate status: {}", resp.getStatusCode());

            Map<String, Object> json = mapper.readValue(resp.getBody(), new TypeReference<Map<String, Object>>(){});
            String webhook = (json.get("webhook") == null) ? null : String.valueOf(json.get("webhook"));
            String accessToken = (json.get("accessToken") == null) ? null : String.valueOf(json.get("accessToken"));

            if ((webhook == null || accessToken == null) && json.containsKey("data")) {
                Map<String, Object> data = mapper.convertValue(json.get("data"), new TypeReference<Map<String, Object>>(){});
                if (webhook == null && data.get("webhook") != null) webhook = String.valueOf(data.get("webhook"));
                if (accessToken == null && data.get("accessToken") != null) accessToken = String.valueOf(data.get("accessToken"));
            }

            GenerateResponse gr = new GenerateResponse();
            gr.setWebhook(webhook);
            gr.setAccessToken(accessToken);
            return gr;
        } catch (HttpClientErrorException e) {
            log.error("HTTP error generateWebhook: {} body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        } catch (Exception e) {
            log.error("Error generateWebhook", e);
            throw new RuntimeException(e);
        }
    }

    public String questionLinkForRegNo(String regNo) {
        String digits = lastTwoDigits(regNo);
        if (digits == null) return "UNKNOWN";
        try {
            int v = Integer.parseInt(digits);
            return (v % 2 == 0) ? QUESTION_EVEN : QUESTION_ODD;
        } catch (NumberFormatException e) {
            return "UNKNOWN";
        }
    }

    private String lastTwoDigits(String regNo) {
        if (regNo == null) return null;
        String digits = regNo.replaceAll("[^0-9]", "");
        if (digits.length() < 2) return null;
        return digits.substring(digits.length() - 2);
    }

    public String loadSqlFromFile(String path) {
        try (FileInputStream fis = new FileInputStream(new File(path))) {
            return StreamUtils.copyToString(fis, StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            log.warn("Could not read SQL from {}: {}", path, e.getMessage());
            return null;
        }
    }

    public void submitFinalQuery(String webhookUrl, String accessToken, String finalQuery) {
        try {
            if (webhookUrl == null || webhookUrl.isBlank()) throw new IllegalArgumentException("Webhook URL required");
            if (finalQuery == null || finalQuery.isBlank()) throw new IllegalArgumentException("finalQuery blank");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            // Spec: Authorization header me JWT token (Bearer mention nahi)
            if (accessToken != null && !accessToken.isBlank()) headers.add("Authorization", accessToken);

            Map<String, Object> payload = new HashMap<>();
            payload.put("finalQuery", finalQuery);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
            ResponseEntity<String> resp = restTemplate.postForEntity(webhookUrl, entity, String.class);
            log.info("Submit status: {} body={}", resp.getStatusCode(), resp.getBody());
        } catch (HttpClientErrorException e) {
            log.error("HTTP error submitFinalQuery: {} body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        } catch (Exception e) {
            log.error("Error submitFinalQuery", e);
            throw new RuntimeException(e);
        }
    }
}
