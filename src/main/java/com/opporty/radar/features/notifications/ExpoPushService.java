package com.opporty.radar.features.notifications;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Servicio que envía notificaciones push a dispositivos móviles
 * a través de la API de Expo Push Notifications.
 *
 * Expo actúa como intermediario hacia FCM (Android) y APNs (iOS).
 * Docs: https://docs.expo.dev/push-notifications/sending-notifications/
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExpoPushService {

    private static final String EXPO_PUSH_URL = "https://exp.host/--/api/v2/push/send";

    private final RestTemplate restTemplate;

    /**
     * Envía una notificación push a un solo dispositivo.
     *
     * @param expoPushToken Token del dispositivo (ej: "ExponentPushToken[xxxx]")
     * @param title         Título de la notificación
     * @param body          Cuerpo del mensaje
     * @param data          Datos extra para deep linking (ej: { "url": "/tabs/(tabs)/event", "eventId": 123 })
     */
    @Async
    public void sendPush(String expoPushToken, String title, String body, Map<String, Object> data) {
        if (expoPushToken == null || expoPushToken.isBlank()) {
            return;
        }

        try {
            Map<String, Object> message = new HashMap<>();
            message.put("to", expoPushToken);
            message.put("title", title);
            message.put("body", body);
            message.put("sound", "default");
            message.put("priority", "high");
            if (data != null && !data.isEmpty()) {
                message.put("data", data);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Accept", "application/json");
            headers.set("Accept-Encoding", "gzip, deflate");

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(message, headers);
            restTemplate.postForEntity(EXPO_PUSH_URL, request, String.class);

            log.info("[ExpoPush] Push enviado a token: {}...{}", 
                    expoPushToken.substring(0, Math.min(20, expoPushToken.length())), 
                    expoPushToken.substring(Math.max(0, expoPushToken.length() - 5)));
        } catch (Exception e) {
            log.error("[ExpoPush] Error al enviar push: {}", e.getMessage());
        }
    }

    /**
     * Envía una notificación push a múltiples dispositivos en batch.
     * La API de Expo admite hasta 100 notificaciones por request.
     *
     * @param tokens Lista de Expo Push Tokens
     * @param title  Título de la notificación
     * @param body   Cuerpo del mensaje
     * @param data   Datos extra para deep linking
     */
    @Async
    public void sendPushBatch(List<String> tokens, String title, String body, Map<String, Object> data) {
        if (tokens == null || tokens.isEmpty()) {
            return;
        }

        // Filtrar tokens nulos o vacíos
        List<String> validTokens = tokens.stream()
                .filter(t -> t != null && !t.isBlank())
                .toList();

        if (validTokens.isEmpty()) {
            return;
        }

        try {
            // Expo admite batch de hasta 100 mensajes por request
            int batchSize = 100;
            for (int i = 0; i < validTokens.size(); i += batchSize) {
                List<String> batch = validTokens.subList(i, Math.min(i + batchSize, validTokens.size()));

                List<Map<String, Object>> messages = new ArrayList<>();
                for (String token : batch) {
                    Map<String, Object> message = new HashMap<>();
                    message.put("to", token);
                    message.put("title", title);
                    message.put("body", body);
                    message.put("sound", "default");
                    message.put("priority", "high");
                    if (data != null && !data.isEmpty()) {
                        message.put("data", data);
                    }
                    messages.add(message);
                }

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("Accept", "application/json");
                headers.set("Accept-Encoding", "gzip, deflate");

                HttpEntity<List<Map<String, Object>>> request = new HttpEntity<>(messages, headers);
                restTemplate.postForEntity(EXPO_PUSH_URL, request, String.class);

                log.info("[ExpoPush] Batch de {} pushes enviado.", batch.size());
            }
        } catch (Exception e) {
            log.error("[ExpoPush] Error al enviar push batch: {}", e.getMessage());
        }
    }
}
