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

    @GetMapping("/threads")
    public ResponseEntity<?> getChatThreads() {
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

        List<Object[]> threadsData = chatMessageRepository.findUniqueThreadsByUserId(userId);
        List<Map<String, Object>> threadsList = new ArrayList<>();
        for (Object[] row : threadsData) {
            Map<String, Object> thread = new HashMap<>();
            thread.put("threadId", row[0]);
            thread.put("title", row[1] != null ? row[1] : "Untitled Chat");
            thread.put("lastUpdated", row[2]);
            threadsList.add(thread);
        }
        return new ResponseEntity<>(threadsList, HttpStatus.OK);
    }

    @GetMapping
    public ResponseEntity<?> getChatHistory(@RequestParam(required = false) String threadId) {
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

        if (threadId != null && !threadId.trim().isEmpty()) {
            List<ChatMessage> history = chatMessageRepository.findByUserIdAndThreadIdOrderByTimestampAsc(userId, threadId.trim());
            return new ResponseEntity<>(history, HttpStatus.OK);
        } else {
            List<Object[]> threads = chatMessageRepository.findUniqueThreadsByUserId(userId);
            if (!threads.isEmpty()) {
                String recentThreadId = (String) threads.get(0)[0];
                List<ChatMessage> history = chatMessageRepository.findByUserIdAndThreadIdOrderByTimestampAsc(userId, recentThreadId);
                return new ResponseEntity<>(history, HttpStatus.OK);
            } else {
                return new ResponseEntity<>(new ArrayList<ChatMessage>(), HttpStatus.OK);
            }
        }
    }

    private String extractTitleFromMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            return "Untitled Chat";
        }
        String clean = message.replaceAll("\\s+", " ").trim();
        String[] words = clean.split(" ");
        StringBuilder sb = new StringBuilder();
        int wordsToTake = Math.min(words.length, 5);
        for (int i = 0; i < wordsToTake; i++) {
            sb.append(words[i]);
            if (i < wordsToTake - 1) {
                sb.append(" ");
            }
        }
        String title = sb.toString();
        if (words.length > 5) {
            title += "...";
        }
        if (title.length() > 35) {
            title = title.substring(0, 32) + "...";
        }
        return title;
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

        String threadId = request.get("threadId");
        if (threadId == null || threadId.trim().isEmpty()) {
            threadId = java.util.UUID.randomUUID().toString();
        } else {
            threadId = threadId.trim();
        }

        try {
            List<ChatMessage> dbHistory = chatMessageRepository.findByUserIdAndThreadIdOrderByTimestampAsc(userId, threadId);
            String requestTitle = request.get("threadTitle");
            String activeTitle = requestTitle;

            if (dbHistory.isEmpty()) {
                if (activeTitle == null || activeTitle.trim().isEmpty()) {
                    activeTitle = extractTitleFromMessage(userMessage);
                } else {
                    activeTitle = activeTitle.trim();
                }
            } else {
                if (requestTitle != null && !requestTitle.trim().isEmpty()) {
                    activeTitle = requestTitle.trim();
                    String firstMsgTitle = dbHistory.get(0).getThreadTitle();
                    if (!activeTitle.equals(firstMsgTitle)) {
                        for (ChatMessage msg : dbHistory) {
                            msg.setThreadTitle(activeTitle);
                        }
                        chatMessageRepository.saveAll(dbHistory);
                    }
                } else {
                    activeTitle = dbHistory.get(0).getThreadTitle();
                }
                if (activeTitle == null || activeTitle.trim().isEmpty()) {
                    activeTitle = "Untitled Chat";
                }
            }

            // 1. Save user message to database
            ChatMessage userChat = ChatMessage.builder()
                    .userId(userId)
                    .role("user")
                    .content(userMessage.trim())
                    .timestamp(LocalDateTime.now())
                    .threadId(threadId)
                    .threadTitle(activeTitle)
                    .build();
            chatMessageRepository.save(userChat);

            // 2. Fetch past conversation history for this thread
            List<ChatMessage> updatedHistory = chatMessageRepository.findByUserIdAndThreadIdOrderByTimestampAsc(userId, threadId);

            // 3. Build List of Content for the model history
            List<Content> contents = new ArrayList<>();
            for (ChatMessage msg : updatedHistory) {
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
                    .threadId(threadId)
                    .threadTitle(activeTitle)
                    .build();
            ChatMessage savedModelChat = chatMessageRepository.save(modelChat);

            return new ResponseEntity<>(savedModelChat, HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Failed to process message: {}", e.getMessage());
            return new ResponseEntity<>("Failed to process message: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @DeleteMapping
    public ResponseEntity<?> clearChatHistory(@RequestParam(required = false) String threadId) {
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
            if (threadId != null && !threadId.trim().isEmpty()) {
                chatMessageRepository.deleteByUserIdAndThreadId(userId, threadId.trim());
            } else {
                chatMessageRepository.deleteByUserId(userId);
            }
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } catch (Exception e) {
            logger.error("Failed to clear chat: {}", e.getMessage());
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
