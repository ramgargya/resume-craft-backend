package com.resumebuilder.backend.controller;

import com.resumebuilder.backend.model.CoverLetter;
import com.resumebuilder.backend.model.AppUser;
import com.resumebuilder.backend.repository.CoverLetterRepository;
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
@RequestMapping("/api/cover-letters")
@CrossOrigin(origins = "*")
public class CoverLetterController {

    private static final Logger logger = LoggerFactory.getLogger(CoverLetterController.class);

    private final CoverLetterRepository coverLetterRepository;
    private final UserRepository userRepository;

    @Autowired
    public CoverLetterController(CoverLetterRepository coverLetterRepository, UserRepository userRepository) {
        this.coverLetterRepository = coverLetterRepository;
        this.userRepository = userRepository;
    }

    private Long getAuthenticatedUserId() {
        UsernamePasswordAuthenticationToken authentication =
                (UsernamePasswordAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) return null;
        return (Long) authentication.getDetails();
    }

    private boolean isPaidUser(Long userId) {
        Optional<AppUser> userOpt = userRepository.findById(userId);
        return userOpt.map(user -> "PAID".equals(user.getSubscriptionTier())).orElse(false);
    }

    @GetMapping
    public ResponseEntity<List<CoverLetter>> getAllCoverLetters() {
        Long userId = getAuthenticatedUserId();
        if (userId == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }

        // Feature lock: Paid users only
        if (!isPaidUser(userId)) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        
        List<CoverLetter> coverLetters = coverLetterRepository.findByUserId(userId);
        return new ResponseEntity<>(coverLetters, HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CoverLetter> getCoverLetterById(@PathVariable Long id) {
        Long userId = getAuthenticatedUserId();
        if (userId == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }

        // Feature lock: Paid users only
        if (!isPaidUser(userId)) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }

        return coverLetterRepository.findById(id)
                .map(cl -> {
                    // Verify ownership
                    if (!cl.getUserId().equals(userId)) {
                        return new ResponseEntity<CoverLetter>(HttpStatus.FORBIDDEN);
                    }
                    return new ResponseEntity<>(cl, HttpStatus.OK);
                })
                .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @PostMapping
    public ResponseEntity<CoverLetter> saveCoverLetter(@RequestBody CoverLetter coverLetter) {
        Long userId = getAuthenticatedUserId();
        if (userId == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }

        // Feature lock: Paid users only
        if (!isPaidUser(userId)) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }

        try {
            coverLetter.setUserId(userId);
            CoverLetter saved = coverLetterRepository.save(coverLetter);
            return new ResponseEntity<>(saved, HttpStatus.CREATED);
        } catch (Exception e) {
            logger.error("Failed to save cover letter: {}", e.getMessage());
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCoverLetter(@PathVariable Long id) {
        Long userId = getAuthenticatedUserId();
        if (userId == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }

        // Feature lock: Paid users only
        if (!isPaidUser(userId)) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }

        try {
            Optional<CoverLetter> clOpt = coverLetterRepository.findById(id);
            if (clOpt.isEmpty()) {
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }

            // Verify ownership
            if (!clOpt.get().getUserId().equals(userId)) {
                return new ResponseEntity<>(HttpStatus.FORBIDDEN);
            }

            coverLetterRepository.deleteById(id);
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } catch (Exception e) {
            logger.error("Failed to delete cover letter: {}", e.getMessage());
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
