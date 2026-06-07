package com.resumebuilder.backend.controller;

import com.resumebuilder.backend.model.Resume;
import com.resumebuilder.backend.model.AppUser;
import com.resumebuilder.backend.service.ResumeService;
import com.resumebuilder.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/resumes")
@CrossOrigin(origins = "*")
public class ResumeController {

    private static final Logger logger = LoggerFactory.getLogger(ResumeController.class);

    private final ResumeService resumeService;
    private final UserRepository userRepository;

    @Autowired
    public ResumeController(ResumeService resumeService, UserRepository userRepository) {
        this.resumeService = resumeService;
        this.userRepository = userRepository;
    }

    private Long getAuthenticatedUserId() {
        UsernamePasswordAuthenticationToken authentication =
                (UsernamePasswordAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) return null;
        return (Long) authentication.getDetails();
    }

    @GetMapping
    public ResponseEntity<List<Resume>> getAllResumes() {
        Long userId = getAuthenticatedUserId();
        if (userId == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
        
        List<Resume> resumes = resumeService.getResumesByUserId(userId);
        return new ResponseEntity<>(resumes, HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Resume> getResumeById(@PathVariable Long id) {
        Long userId = getAuthenticatedUserId();
        if (userId == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }

        return resumeService.getResumeById(id)
                .map(resume -> {
                    // SECURE ACCESS: Verify that the requesting user owns this resume
                    if (!resume.getUserId().equals(userId)) {
                        return new ResponseEntity<Resume>(HttpStatus.FORBIDDEN);
                    }
                    return new ResponseEntity<>(resume, HttpStatus.OK);
                })
                .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @PostMapping
    public ResponseEntity<Resume> saveResume(@RequestBody Resume resume) {
        Long userId = getAuthenticatedUserId();
        if (userId == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }

        try {
            // SECURE INJECTION: Enforce database ownership using authenticated session metadata
            resume.setUserId(userId);

            // Limit Enforcement: FREE users are limited to 2 resumes
            if (resume.getId() == null) {
                AppUser user = userRepository.findById(userId).orElse(null);
                if (user == null || !"PAID".equals(user.getSubscriptionTier())) {
                    List<Resume> existing = resumeService.getResumesByUserId(userId);
                    if (existing.size() >= 2) {
                        return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
                    }
                }
            }

            Resume savedResume = resumeService.saveResume(resume);
            return new ResponseEntity<>(savedResume, HttpStatus.CREATED);
        } catch (Exception e) {
            logger.error("Failed to save resume: {}", e.getMessage());
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteResume(@PathVariable Long id) {
        Long userId = getAuthenticatedUserId();
        if (userId == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }

        try {
            Optional<Resume> resumeOpt = resumeService.getResumeById(id);
            if (resumeOpt.isEmpty()) {
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }

            // SECURE DELETION: Block unauthorized attempts to delete other accounts' records
            if (!resumeOpt.get().getUserId().equals(userId)) {
                return new ResponseEntity<>(HttpStatus.FORBIDDEN);
            }

            resumeService.deleteResume(id);
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } catch (Exception e) {
            logger.error("Failed to delete resume: {}", e.getMessage());
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
