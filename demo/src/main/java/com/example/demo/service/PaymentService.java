package com.example.demo.service;

import com.example.demo.model.Order;
import com.example.demo.model.OrderStatus;
import com.example.demo.repository.OrderRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PaymentService {

    @Value("${cashfree.client-id}")
    private String clientId;

    @Value("${cashfree.client-secret}")
    private String clientSecret;

    @Value("${cashfree.base-url}")
    private String baseUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final OrderRepository orderRepository;

    // Dedupe store for webhook events — keyed on Cashfree's own payment ID,
    // not anything we generate. Prevents double-processing a retried webhook.
    private final Map<String, Boolean> processedEvents = new ConcurrentHashMap<>();

    public PaymentService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    // ---------- 1. Create order + get Cashfree checkout session ----------

    public Order createOrderAndSession(BigDecimal amount) {
        Order order = new Order();
        order.setId("order_" + UUID.randomUUID());
        order.setAmount(amount);
        order.setStatus(OrderStatus.PENDING);

        Map<String, Object> customerDetails = new HashMap<>();
        customerDetails.put("customer_id", "cust_" + UUID.randomUUID());
        customerDetails.put("customer_phone", "9999999999");
        Map<String , Object> orderMeta = new HashMap<>();
        orderMeta.put(
                "notify_url",
                "https://turf-supervise-reckless.ngrok-free.dev/orders/webhook/cashfree"
        );


        Map<String, Object> payload = new HashMap<>();
        payload.put("order_id", order.getId());
        payload.put("order_amount", amount);
        payload.put("order_currency", "INR");
        payload.put("customer_details", customerDetails);
        payload.put("order_meta", orderMeta);

        HttpHeaders headers = new HttpHeaders();
        headers.set("x-client-id", clientId);
        headers.set("x-client-secret", clientSecret);
        headers.set("x-api-version", "2023-08-01");
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl + "/orders", request, Map.class);

        String sessionId = (String) response.getBody().get("payment_session_id");
        order.setPaymentSessionId(sessionId);
        order.setStatus(OrderStatus.PROCESSING);

        orderRepository.save(order);

        return order;
    }

    // ---------- 2. Manually pull an order's real status from Cashfree ----------

    public Map getOrderStatus(String orderId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-client-id", clientId);
        headers.set("x-client-secret", clientSecret);
        headers.set("x-api-version", "2023-08-01");

        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl + "/orders/" + orderId,
                HttpMethod.GET,
                request,
                Map.class
        );

        return response.getBody();
    }

    // ---------- 3. Webhook signature verification ----------

    public boolean verifyWebhookSignature(String rawBody, String signature, String timestamp) {
        try {
            String signedPayload = timestamp + rawBody;
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(clientSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = hmac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash).equals(signature);
        } catch (Exception e) {
            return false;
        }
    }

    // ---------- 4. Process an incoming webhook, safely, even if retried ----------

    public void processWebhook(String rawBody) throws Exception {
        JsonMapper mapper = JsonMapper.builder().build();
        JsonNode root = mapper.readTree(rawBody);
        JsonNode data = root.path("data");

        String orderId = data.path("order").path("order_id").asString();
        String paymentStatus = data.path("payment").path("payment_status").asString();
        String cfPaymentId = data.path("payment").path("cf_payment_id").asString();

        if (processedEvents.putIfAbsent(cfPaymentId, true) != null) {
            return; // already handled this exact event
        }

        Order order = orderRepository.findById(orderId).orElse(null);
        if (order != null) {
            order.setStatus("SUCCESS".equals(paymentStatus) ? OrderStatus.COMPLETED : OrderStatus.REJECTED);
            orderRepository.save(order);
        }
    }
}