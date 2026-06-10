package com.resumebuilder.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumebuilder.backend.model.EducationDetail;
import com.resumebuilder.backend.model.ExperienceDetail;
import com.resumebuilder.backend.model.Resume;
import com.resumebuilder.backend.service.ResumeService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;

@SpringBootTest
class BackendApplicationTests {

    @Autowired
    private ResumeService resumeService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void contextLoads() {
    }

    @Test
    void testResumeSerialization() throws Exception {
        // Create a new resume with some nested collections
        Resume resume = Resume.builder()
                .name("John Doe")
                .email("john.doe@example.com")
                .phone("1234567890")
                .userId(999L)
                .experienceDetails(new ArrayList<>())
                .educationDetails(new ArrayList<>())
                .build();

        ExperienceDetail exp = ExperienceDetail.builder()
                .companyName("Acme Corp")
                .role("Developer")
                .resume(resume)
                .build();
        resume.getExperienceDetails().add(exp);

        EducationDetail edu = EducationDetail.builder()
                .institution("University of Coding")
                .degree("B.S.")
                .resume(resume)
                .build();
        resume.getEducationDetails().add(edu);

        // Save resume
        Resume saved = resumeService.saveResume(resume);
        Assertions.assertNotNull(saved.getId());

        // Fetch resume by userId (which should initialize collections)
        List<Resume> fetchedList = resumeService.getResumesByUserId(999L);
        Assertions.assertFalse(fetchedList.isEmpty());
        Resume fetched = fetchedList.get(0);

        // Try to serialize the fetched resume to JSON outside of a transaction context
        String json = objectMapper.writeValueAsString(fetched);
        
        // Assert that the JSON contains the expected detail properties
        Assertions.assertTrue(json.contains("Acme Corp"));
        Assertions.assertTrue(json.contains("Developer"));
        Assertions.assertTrue(json.contains("University of Coding"));
        Assertions.assertTrue(json.contains("B.S."));

        // Clean up
        resumeService.deleteResume(saved.getId());
    }

    @Autowired
    private com.resumebuilder.backend.repository.UserRepository userRepository;

    @Autowired
    private com.resumebuilder.backend.repository.ChatMessageRepository chatMessageRepository;

    @Test
    void printDatabaseStats() {
        System.out.println("=== DIAGNOSTIC DATABASE STATS ===");
        System.out.println("Registered Users count: " + userRepository.count());
        userRepository.findAll().forEach(user -> {
            System.out.println("User: id=" + user.getId() + ", email=" + user.getEmail() + ", name=" + user.getName() + ", subscriptionTier=" + user.getSubscriptionTier() + ", role=" + user.getRole());
        });
        System.out.println("Chat Messages count: " + chatMessageRepository.count());
        chatMessageRepository.findAll().forEach(msg -> {
            System.out.println("ChatMessage: id=" + msg.getId() + ", userId=" + msg.getUserId() + ", threadId=" + msg.getThreadId() + ", threadTitle=" + msg.getThreadTitle() + ", role=" + msg.getRole() + ", content=" + msg.getContent());
        });
        System.out.println("=================================");
    }

    @Autowired
    private com.resumebuilder.backend.controller.ChatController chatController;

    @Test
    void testUnsavedResumeChatFlow() {
        // 1. Create a dummy user
        com.resumebuilder.backend.model.AppUser testUser = com.resumebuilder.backend.model.AppUser.builder()
                .email("unsaved-test@example.com")
                .name("Unsaved Test User")
                .password("password")
                .provider("LOCAL")
                .verified(true)
                .subscriptionTier("PAID")
                .build();
        testUser = userRepository.save(testUser);
        Long userId = testUser.getId();

        // Set up security context authentication for controller calls
        org.springframework.security.authentication.UsernamePasswordAuthenticationToken auth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        testUser.getEmail(), null, java.util.Collections.emptyList()
                );
        auth.setDetails(userId);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);

        try {
            // 2. Chat with unsaved resume thread
            java.util.Map<String, Object> chatRequest = new java.util.HashMap<>();
            chatRequest.put("message", "Suggest a project in Spring Boot.");
            chatRequest.put("threadId", "resume-unsaved");
            chatRequest.put("threadTitle", "untitled - resume");

            org.springframework.http.ResponseEntity<?> chatResponse = chatController.sendMessage(chatRequest);
            Assertions.assertEquals(org.springframework.http.HttpStatus.OK, chatResponse.getStatusCode());

            // 3. Close & Reopen: Fetch history for resume-unsaved
            org.springframework.http.ResponseEntity<?> historyResponse = chatController.getChatHistory("resume-unsaved");
            Assertions.assertEquals(org.springframework.http.HttpStatus.OK, historyResponse.getStatusCode());
            java.util.List<?> history = (java.util.List<?>) historyResponse.getBody();
            Assertions.assertNotNull(history);
            Assertions.assertTrue(history.size() >= 2); // 1 user message + 1 model reply

            // 4. Save the resume for the first time
            Resume resume = Resume.builder()
                    .name("My First Saved Resume")
                    .email("unsaved-test@example.com")
                    .userId(userId)
                    .experienceDetails(new ArrayList<>())
                    .build();
            Resume savedResume = resumeService.saveResume(resume);
            Assertions.assertNotNull(savedResume.getId());

            // 5. Verify thread ID is migrated from "resume-unsaved" to "resume-<id>"
            String expectedNewThreadId = "resume-" + savedResume.getId();
            List<com.resumebuilder.backend.model.ChatMessage> oldHistory = chatMessageRepository.findByUserIdAndThreadIdOrderByTimestampAsc(userId, "resume-unsaved");
            Assertions.assertTrue(oldHistory.isEmpty(), "Old thread should be migrated and empty");

            List<com.resumebuilder.backend.model.ChatMessage> newHistory = chatMessageRepository.findByUserIdAndThreadIdOrderByTimestampAsc(userId, expectedNewThreadId);
            Assertions.assertFalse(newHistory.isEmpty(), "Migrated thread should not be empty");
            Assertions.assertTrue(newHistory.size() >= 2);
            Assertions.assertEquals("My First Saved Resume - resume", newHistory.get(0).getThreadTitle());

            // Clean up
            chatMessageRepository.deleteByUserId(userId);
            resumeService.deleteResume(savedResume.getId());
        } finally {
            userRepository.delete(testUser);
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }
}


