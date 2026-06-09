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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

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

    @SuppressWarnings("unchecked")
    private String formatDocumentContext(Map<String, Object> docContext) {
        if (docContext == null) return "";
        try {
            String type = (String) docContext.get("type");
            Map<String, Object> data = (Map<String, Object>) docContext.get("data");
            if (data == null) return "";

            StringBuilder sb = new StringBuilder();
            sb.append("\n\nHere is the current real-time content of the user's document they are currently editing:\n");
            sb.append("Document Type: ").append(type).append("\n");

            if ("resume_form".equals(type)) {
                sb.append("--- RESUME FORM DETAILS ---\n");
                sb.append("Name: ").append(data.get("name")).append("\n");
                sb.append("Email: ").append(data.get("email")).append("\n");
                sb.append("Phone: ").append(data.get("phone")).append("\n");
                sb.append("Experience Type: ").append(data.get("experienceType")).append("\n");
                sb.append("Summary: ").append(data.get("summary")).append("\n");
                
                List<Map<String, Object>> experiences = (List<Map<String, Object>>) data.get("experienceDetails");
                if (experiences != null && !experiences.isEmpty()) {
                    sb.append("\nWork Experience:\n");
                    for (Map<String, Object> exp : experiences) {
                        sb.append("- ").append(exp.get("role")).append(" at ").append(exp.get("companyName"))
                          .append(" (").append(exp.get("startDate")).append(" to ").append(exp.get("endDate")).append(")\n")
                          .append("  Description: ").append(exp.get("description")).append("\n");
                    }
                }
                
                List<Map<String, Object>> education = (List<Map<String, Object>>) data.get("educationDetails");
                if (education != null && !education.isEmpty()) {
                    sb.append("\nEducation:\n");
                    for (Map<String, Object> edu : education) {
                        sb.append("- ").append(edu.get("degree")).append(" in ").append(edu.get("fieldOfStudy"))
                          .append(" at ").append(edu.get("institution")).append(" (Graduation: ").append(edu.get("graduationDate")).append(")\n");
                    }
                }
                
                List<Map<String, Object>> projects = (List<Map<String, Object>>) data.get("projectDetails");
                if (projects != null && !projects.isEmpty()) {
                    sb.append("\nProjects:\n");
                    for (Map<String, Object> proj : projects) {
                        sb.append("- ").append(proj.get("title")).append(" (Tech: ").append(proj.get("technologies")).append(")\n")
                          .append("  Link: ").append(proj.get("link")).append("\n")
                          .append("  Description: ").append(proj.get("description")).append("\n");
                    }
                }

                List<Map<String, Object>> skills = (List<Map<String, Object>>) data.get("skills");
                if (skills != null && !skills.isEmpty()) {
                    sb.append("\nSkills:\n");
                    for (Map<String, Object> skill : skills) {
                        sb.append("- ").append(skill.get("name")).append(" (Proficiency: ").append(skill.get("proficiency")).append(")\n");
                    }
                }

                List<String> achievements = (List<String>) data.get("achievements");
                if (achievements != null && !achievements.isEmpty()) {
                    sb.append("\nAchievements:\n");
                    for (String ach : achievements) {
                        sb.append("- ").append(ach).append("\n");
                    }
                }

                List<String> activities = (List<String>) data.get("extracurriculars");
                if (activities != null && !activities.isEmpty()) {
                    sb.append("\nExtracurricular Activities:\n");
                    for (String act : activities) {
                        sb.append("- ").append(act).append("\n");
                    }
                }

                List<String> hobbies = (List<String>) data.get("hobbies");
                if (hobbies != null && !hobbies.isEmpty()) {
                    sb.append("\nHobbies:\n");
                    for (String hb : hobbies) {
                        sb.append("- ").append(hb).append("\n");
                    }
                }

            } else if ("resume_whiteboard".equals(type)) {
                sb.append("--- RESUME WHITEBOARD DETAILS ---\n");
                sb.append("General Info:\n");
                sb.append("Name: ").append(data.get("name")).append("\n");
                sb.append("Email: ").append(data.get("email")).append("\n");
                sb.append("Phone: ").append(data.get("phone")).append("\n");
                
                String whiteboardDataStr = (String) data.get("whiteboardData");
                if (whiteboardDataStr != null && !whiteboardDataStr.trim().isEmpty()) {
                    try {
                        ObjectMapper mapper = new ObjectMapper();
                        List<Map<String, Object>> elements = mapper.readValue(whiteboardDataStr, new TypeReference<List<Map<String, Object>>>() {});
                        if (elements != null && !elements.isEmpty()) {
                            sb.append("\nWhiteboard Canvas Elements:\n");
                            for (Map<String, Object> el : elements) {
                                String elType = (String) el.get("type");
                                int page = el.get("page") != null ? ((Number) el.get("page")).intValue() + 1 : 1;
                                if ("text".equals(elType)) {
                                    sb.append("- [Page ").append(page).append(" Text Element]: \"")
                                      .append(el.get("text")).append("\" (Size: ").append(el.get("fontSize")).append("px)\n");
                                } else if ("icon".equals(elType)) {
                                    sb.append("- [Page ").append(page).append(" Icon]: ").append(el.get("iconType")).append("\n");
                                } else if ("shape".equals(elType)) {
                                    sb.append("- [Page ").append(page).append(" Shape]: ").append(el.get("shapeType")).append("\n");
                                }
                            }
                        }
                    } catch (Exception e) {
                        sb.append("\nRaw Whiteboard Data (JSON String):\n").append(whiteboardDataStr).append("\n");
                    }
                }
            } else if ("cover_letter".equals(type)) {
                sb.append("--- COVER LETTER DETAILS ---\n");
                sb.append("Sender Name: ").append(data.get("senderName")).append("\n");
                sb.append("Sender Email: ").append(data.get("senderEmail")).append("\n");
                sb.append("Sender Phone: ").append(data.get("senderPhone")).append("\n");
                sb.append("Recipient: ").append(data.get("recipientName")).append(", ").append(data.get("recipientTitle"))
                  .append(" at ").append(data.get("recipientCompany")).append("\n");
                sb.append("Recipient Address: ").append(data.get("recipientAddress")).append("\n");
                sb.append("Date: ").append(data.get("dateText")).append("\n");
                sb.append("Subject: ").append(data.get("subjectLine")).append("\n");
                sb.append("Salutation: ").append(data.get("salutation")).append("\n");
                sb.append("\nLetter Body:\n").append(data.get("letterBody")).append("\n");
                sb.append("\nSign Off:\n").append(data.get("signOff")).append("\n");
            }

            return sb.toString();
        } catch (Exception e) {
            logger.error("Failed to format document context", e);
            return "";
        }
    }

    @PostMapping
    public ResponseEntity<?> sendMessage(@RequestBody Map<String, Object> request) {
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

        String userMessage = (String) request.get("message");
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return new ResponseEntity<>("Message content is required.", HttpStatus.BAD_REQUEST);
        }

        String threadId = (String) request.get("threadId");
        if (threadId == null || threadId.trim().isEmpty()) {
            threadId = java.util.UUID.randomUUID().toString();
        } else {
            threadId = threadId.trim();
        }

        try {
            List<ChatMessage> dbHistory = chatMessageRepository.findByUserIdAndThreadIdOrderByTimestampAsc(userId, threadId);
            String requestTitle = (String) request.get("threadTitle");
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

            // 4. Set up system instructions with real-time document context if available
            String systemPromptText = "You are a resume expert, ATS checker, and resume corrector. " +
                    "You must exclusively answer questions related to resumes (such as resume sections, layouts, grammar, ATS compatibility, skills, professional summaries, and career/job applications). " +
                    "If the user asks an out-of-scope question or any question not related to resumes, you must gracefully decline to answer (e.g., \"I'm sorry, but I can only answer questions related to resumes.\"). " +
                    "All answers generated must be strictly in a short, concise format without any unnecessary filler or conversational fluff.";

            Object docContextObj = request.get("documentContext");
            if (docContextObj instanceof Map) {
                String formattedContext = formatDocumentContext((Map<String, Object>) docContextObj);
                if (!formattedContext.isEmpty()) {
                    systemPromptText += formattedContext;
                    systemPromptText += "\n\nYou should refer to this current document context to answer the user's questions directly (e.g. if the user asks 'is my experience section good?', evaluate the experience details provided in this context). Do not ask the user to provide the content again unless it is missing from the context.";
                }
            }

            Content systemInstruction = Content.fromParts(Part.fromText(systemPromptText));

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
