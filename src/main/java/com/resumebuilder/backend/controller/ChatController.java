package com.resumebuilder.backend.controller;

import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.resumebuilder.backend.model.AppUser;
import com.resumebuilder.backend.model.ChatMessage;
import com.resumebuilder.backend.repository.ChatMessageRepository;
import com.resumebuilder.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
public class ChatController {

    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);

    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final String geminiApiKey;
    private Client client;

    @Autowired
    public ChatController(
            ChatMessageRepository chatMessageRepository,
            UserRepository userRepository,
            @Value("${gemini.api.key}") String geminiApiKey) {
        this.chatMessageRepository = chatMessageRepository;
        this.userRepository = userRepository;
        this.geminiApiKey = geminiApiKey;
    }

    private synchronized Client getClient() {
        if (client == null) {
            client = Client.builder().apiKey(geminiApiKey).build();
        }
        return client;
    }

    private Long getAuthenticatedUserId() {
        UsernamePasswordAuthenticationToken authentication =
                (UsernamePasswordAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) return null;
        return (Long) authentication.getDetails();
    }

    @GetMapping
    public ResponseEntity<?> getChatHistory() {
        Long userId = getAuthenticatedUserId();
        if (userId == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }

        AppUser user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
        if (!"PAID".equals(user.getSubscriptionTier()) && !"ADMIN".equals(user.getRole())) {
            return new ResponseEntity<>("Please upgrade to the Paid plan to use the AI chat assistant.", HttpStatus.FORBIDDEN);
        }

        List<ChatMessage> history = chatMessageRepository.findByUserIdOrderByTimestampAsc(userId);
        return new ResponseEntity<>(history, HttpStatus.OK);
    }

    @PostMapping
    public ResponseEntity<?> sendMessage(@RequestBody Map<String, String> request) {
        Long userId = getAuthenticatedUserId();
        if (userId == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }

        AppUser user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
        if (!"PAID".equals(user.getSubscriptionTier()) && !"ADMIN".equals(user.getRole())) {
            return new ResponseEntity<>("Please upgrade to the Paid plan to use the AI chat assistant.", HttpStatus.FORBIDDEN);
        }

        String userMessage = request.get("message");
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return new ResponseEntity<>("Message content is required.", HttpStatus.BAD_REQUEST);
        }

        try {
            // 1. Save user message to database
            ChatMessage userChat = ChatMessage.builder()
                    .userId(userId)
                    .role("user")
                    .content(userMessage.trim())
                    .timestamp(LocalDateTime.now())
                    .build();
            chatMessageRepository.save(userChat);

            // 2. Fetch past conversation history
            List<ChatMessage> dbHistory = chatMessageRepository.findByUserIdOrderByTimestampAsc(userId);

            // 3. Build List of Content for the model history
            List<Content> contents = new ArrayList<>();
            for (ChatMessage msg : dbHistory) {
                // Determine the correct role name for the Gemini API:
                // "user" represents user turn, "model" represents model turn.
                String role = msg.getRole();
                if (!"user".equals(role) && !"model".equals(role)) {
                    role = "user";
                }
                
                Content contentTurn = Content.builder()
                        .role(role)
                        .parts(List.of(Part.fromText(msg.getContent())))
                        .build();
                contents.add(contentTurn);
            }

            // 4. Set up system instructions
            Content systemInstruction = Content.fromParts(
                    Part.fromText("You are a resume expert, ATS checker, and resume corrector. " +
                            "You must exclusively answer questions related to resumes (such as resume sections, layouts, grammar, ATS compatibility, skills, professional summaries, and career/job applications). " +
                            "If the user asks an out-of-scope question or any question not related to resumes, you must gracefully decline to answer (e.g., \"I'm sorry, but I can only answer questions related to resumes.\"). " +
                            "All answers generated must be strictly in a short, concise format without any unnecessary filler or conversational fluff.")
            );

            GenerateContentConfig config = GenerateContentConfig.builder()
                    .systemInstruction(systemInstruction)
                    .build();

            // 5. Invoke Gemini API
            Client geminiClient = getClient();
            GenerateContentResponse geminiResponse = geminiClient.models.generateContent(
                    "gemini-2.5-flash",
                    contents,
                    config
            );

            String modelReply = geminiResponse.text();
            if (modelReply == null) {
                modelReply = "Sorry, I couldn't process your request.";
            }

            // 6. Save model response to database
            ChatMessage modelChat = ChatMessage.builder()
                    .userId(userId)
                    .role("model")
                    .content(modelReply)
                    .timestamp(LocalDateTime.now())
                    .build();
            ChatMessage savedModelChat = chatMessageRepository.save(modelChat);

            return new ResponseEntity<>(savedModelChat, HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Failed to process message: {}", e.getMessage());
            return new ResponseEntity<>("Failed to process message: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @DeleteMapping
    public ResponseEntity<?> clearChatHistory() {
        Long userId = getAuthenticatedUserId();
        if (userId == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }

        AppUser user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
        if (!"PAID".equals(user.getSubscriptionTier()) && !"ADMIN".equals(user.getRole())) {
            return new ResponseEntity<>("Please upgrade to the Paid plan to use the AI chat assistant.", HttpStatus.FORBIDDEN);
        }

        try {
            chatMessageRepository.deleteByUserId(userId);
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
