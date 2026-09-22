package com.example.demo.controller;

import com.example.demo.model.Order;
import com.example.demo.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final PaymentService paymentService;

    public OrderController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public Order createOrder(@RequestParam BigDecimal amount) {
        return paymentService.createOrderAndSession(amount);
    }

    @PostMapping("/webhook/cashfree")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String rawBody,
            @RequestHeader("x-webhook-signature") String signature,
            @RequestHeader("x-webhook-timestamp") String timestamp) throws Exception {
        System.out.println("========== CASHFREE WEBHOOK ==========");
        System.out.println(rawBody);
        System.out.println("======================================");

        if (!paymentService.verifyWebhookSignature(rawBody, signature, timestamp)) {
            return ResponseEntity.status(401).body("invalid signature");
        }
        paymentService.processWebhook(rawBody);
        return ResponseEntity.ok("ok");
    }
}