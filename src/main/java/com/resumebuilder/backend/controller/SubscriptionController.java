package com.resumebuilder.backend.controller;

import com.resumebuilder.backend.model.AppUser;
import com.resumebuilder.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/subscription")
@CrossOrigin(origins = "*")
public class SubscriptionController {

    private static final Logger logger = LoggerFactory.getLogger(SubscriptionController.class);

    private final UserRepository userRepository;

    @Value("${razorpay.key.id}")
    private String razorpayKeyId;

    @Value("${razorpay.key.secret}")
    private String razorpayKeySecret;

    @Autowired
    public SubscriptionController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    private Long getAuthenticatedUserId() {
        UsernamePasswordAuthenticationToken authentication =
                (UsernamePasswordAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) return null;
        return (Long) authentication.getDetails();
    }

    @PostMapping("/upgrade/order")
    public ResponseEntity<?> createUpgradeOrder() {
        Long userId = getAuthenticatedUserId();
        if (userId == null) {
            return new ResponseEntity<>("Unauthorized", HttpStatus.UNAUTHORIZED);
        }

        try {
            String auth = razorpayKeyId + ":" + razorpayKeySecret;
            byte[] encodedAuth = java.util.Base64.getEncoder().encode(auth.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            String authHeader = "Basic " + new String(encodedAuth);

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            headers.set("Authorization", authHeader);

            Map<String, Object> body = new HashMap<>();
            body.put("amount", 19900); // 199.00 INR in paise
            body.put("currency", "INR");
            body.put("receipt", "receipt_upgrade_" + userId);

            org.springframework.http.HttpEntity<Map<String, Object>> entity = new org.springframework.http.HttpEntity<>(body, headers);
            org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
            
            ResponseEntity<Map> response = restTemplate.postForEntity("https://api.razorpay.com/v1/orders", entity, Map.class);
            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null || !responseBody.containsKey("id")) {
                return new ResponseEntity<>("Failed to create Razorpay order", HttpStatus.INTERNAL_SERVER_ERROR);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("id", responseBody.get("id"));
            result.put("amount", 19900);
            result.put("currency", "INR");
            result.put("key", razorpayKeyId);

            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Error creating Razorpay order: {}", e.getMessage());
            return new ResponseEntity<>("Error creating payment order: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/verify-payment")
    @Transactional
    public ResponseEntity<?> verifyPayment(@RequestBody Map<String, String> payload) {
        Long userId = getAuthenticatedUserId();
        if (userId == null) {
            return new ResponseEntity<>("Unauthorized", HttpStatus.UNAUTHORIZED);
        }

        String orderId = payload.get("razorpay_order_id");
        String paymentId = payload.get("razorpay_payment_id");
        String signature = payload.get("razorpay_signature");

        if (orderId == null || paymentId == null || signature == null) {
            return new ResponseEntity<>("Missing payment verification details.", HttpStatus.BAD_REQUEST);
        }

        try {
            String data = orderId + "|" + paymentId;
            String expectedSignature = calculateHmacSha256(data, razorpayKeySecret);

            if (!expectedSignature.equals(signature)) {
                logger.warn("Razorpay payment signature mismatch for user id: {}", userId);
                return new ResponseEntity<>("Payment verification failed. Invalid signature.", HttpStatus.BAD_REQUEST);
            }

            Optional<AppUser> userOpt = userRepository.findById(userId);
            if (userOpt.isEmpty()) {
                return new ResponseEntity<>("User not found", HttpStatus.NOT_FOUND);
            }

            AppUser user = userOpt.get();
            user.setSubscriptionTier("PAID");
            userRepository.save(user);

            logger.info("User subscription upgraded to PAID for user id: {}", userId);

            Map<String, Object> responsePayload = new HashMap<>();
            responsePayload.put("id", user.getId());
            responsePayload.put("email", user.getEmail());
            responsePayload.put("name", user.getName());
            responsePayload.put("provider", user.getProvider());
            responsePayload.put("role", user.getRole());
            responsePayload.put("subscriptionTier", user.getSubscriptionTier());

            return new ResponseEntity<>(responsePayload, HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Error verifying payment: {}", e.getMessage());
            return new ResponseEntity<>("Error processing payment verification.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String calculateHmacSha256(String data, String secret) throws Exception {
        javax.crypto.spec.SecretKeySpec signingKey = new javax.crypto.spec.SecretKeySpec(
                secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256");
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(signingKey);
        byte[] rawHmac = mac.doFinal(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        
        StringBuilder hexString = new StringBuilder();
        for (byte b : rawHmac) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
