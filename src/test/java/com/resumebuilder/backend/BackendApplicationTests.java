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
}
