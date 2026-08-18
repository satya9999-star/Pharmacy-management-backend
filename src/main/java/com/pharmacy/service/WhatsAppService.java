package com.pharmacy.service;

import com.pharmacy.model.StoreConfig;
import com.pharmacy.repository.StoreConfigRepository;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class WhatsAppService {
    private final StoreConfigRepository configRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    public WhatsAppService(StoreConfigRepository configRepository) {
        this.configRepository = configRepository;
    }

    public void sendWhatsAppMessage(String cleanPhone, String messageText) {
        sendWhatsAppDocument(cleanPhone, messageText, null, null);
    }

    public void sendWhatsAppDocument(String cleanPhone, String messageText, String pdfBase64, String filename) {
        StoreConfig config = configRepository.findById(1L).orElse(null);
        if (config == null) {
            System.out.println("[WhatsApp Service] No configuration found. Log: Phone: " + cleanPhone + ", Message: " + messageText);
            return;
        }
        
        String url = config.whatsappGatewayUrl;
        if (url == null || url.trim().isEmpty()) {
            System.out.println("[WhatsApp Service] Gateway URL not configured. Log: Phone: " + cleanPhone + ", Message: " + messageText);
            return;
        }

        new Thread(() -> {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                if (config.whatsappToken != null && !config.whatsappToken.trim().isEmpty()) {
                    String token = config.whatsappToken.trim();
                    headers.set("Authorization", "Bearer " + token);
                    headers.set("token", token);
                    headers.set("x-api-key", token);
                    headers.set("apikey", token);
                    headers.set("access-token", token);
                }

                String docName = (filename != null && !filename.trim().isEmpty()) ? filename.trim() : "Invoice.pdf";
                boolean isPdf = pdfBase64 != null && !pdfBase64.trim().isEmpty();

                Map<String, Object> body = new HashMap<>();
                body.put("phone", cleanPhone);
                body.put("number", cleanPhone);
                body.put("to", cleanPhone);
                body.put("chatId", cleanPhone + "@c.us");
                body.put("receiver", cleanPhone);
                body.put("sender", config.whatsappSender);
                body.put("from", config.whatsappSender);

                if (isPdf) {
                    String rawBase64 = pdfBase64.contains(",") ? pdfBase64.split(",")[1].trim() : pdfBase64.trim();
                    String dataUrl = "data:application/pdf;base64," + rawBase64;
                    
                    body.put("caption", messageText);
                    body.put("message", messageText);
                    body.put("text", messageText);
                    
                    body.put("isMedia", true);
                    body.put("isDocument", true);
                    body.put("hasMedia", true);
                    body.put("mediaType", "document");
                    body.put("type", "document");
                    body.put("mimeType", "application/pdf");
                    body.put("mimetype", "application/pdf");
                    body.put("filename", docName);
                    body.put("fileName", docName);
                    body.put("name", docName);

                    body.put("pdfBase64", rawBase64);
                    body.put("document", rawBase64);
                    body.put("file", rawBase64);
                    body.put("media", rawBase64);
                    body.put("base64", rawBase64);
                    body.put("attachment", rawBase64);
                    body.put("data", rawBase64);

                    body.put("url", dataUrl);
                    body.put("fileUrl", dataUrl);
                    body.put("mediaUrl", dataUrl);
                    body.put("documentUrl", dataUrl);
                } else {
                    body.put("message", messageText);
                    body.put("caption", messageText);
                    body.put("text", messageText);
                }

                HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);
                System.out.println("[WhatsApp Service] Dispatching PDF Document request to Gateway: " + url);
                ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);
                System.out.println("[WhatsApp Service] Gateway API response (" + response.getStatusCode() + "): " + response.getBody());
            } catch (Exception e) {
                System.err.println("[WhatsApp Service] Failed to send PDF message via API Gateway: " + e.getMessage());
            }
        }).start();
    }
}
