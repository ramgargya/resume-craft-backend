package com.resumebuilder.backend.controller;

import com.resumebuilder.backend.model.AppUser;
import com.resumebuilder.backend.repository.UserRepository;
import com.resumebuilder.backend.repository.ResumeRepository;
import com.resumebuilder.backend.repository.ChatMessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*")
public class AdminController {

    private final UserRepository userRepository;
    private final ResumeRepository resumeRepository;
    private final ChatMessageRepository chatMessageRepository;

    @Autowired
    public AdminController(UserRepository userRepository, 
                           ResumeRepository resumeRepository,
                           ChatMessageRepository chatMessageRepository) {
        this.userRepository = userRepository;
        this.resumeRepository = resumeRepository;
        this.chatMessageRepository = chatMessageRepository;
    }

    private AppUser getAuthenticatedAdmin() {
        org.springframework.security.core.Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication instanceof UsernamePasswordAuthenticationToken)) {
            return null;
        }
        Long userId = (Long) ((UsernamePasswordAuthenticationToken) authentication).getDetails();
        if (userId == null) return null;
        AppUser user = userRepository.findById(userId).orElse(null);
        if (user != null && "ADMIN".equals(user.getRole())) {
            return user;
        }
        return null;
    }

    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers() {
        if (getAuthenticatedAdmin() == null) {
            return new ResponseEntity<>("Access denied. Admin role required.", HttpStatus.FORBIDDEN);
        }
        List<AppUser> allUsers = userRepository.findAll();
        List<Map<String, Object>> response = new ArrayList<>();
        for (AppUser u : allUsers) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", u.getId());
            map.put("email", u.getEmail());
            map.put("name", u.getName());
            map.put("provider", u.getProvider());
            map.put("role", u.getRole());
            map.put("subscriptionTier", u.getSubscriptionTier());
            response.add(map);
        }
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PutMapping("/users/{id}/subscription")
    @Transactional
    public ResponseEntity<?> updateUserSubscription(@PathVariable Long id, @RequestBody Map<String, String> request) {
        if (getAuthenticatedAdmin() == null) {
            return new ResponseEntity<>("Access denied. Admin role required.", HttpStatus.FORBIDDEN);
        }
        String tier = request.get("subscriptionTier");
        if (tier == null || (!"FREE".equals(tier) && !"PAID".equals(tier))) {
            return new ResponseEntity<>("Invalid subscription tier. Must be FREE or PAID.", HttpStatus.BAD_REQUEST);
        }
        Optional<AppUser> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) {
            return new ResponseEntity<>("User not found.", HttpStatus.NOT_FOUND);
        }
        AppUser user = userOpt.get();
        user.setSubscriptionTier(tier);
        userRepository.save(user);

        Map<String, Object> map = new HashMap<>();
        map.put("id", user.getId());
        map.put("email", user.getEmail());
        map.put("name", user.getName());
        map.put("provider", user.getProvider());
        map.put("role", user.getRole());
        map.put("subscriptionTier", user.getSubscriptionTier());
        return new ResponseEntity<>(map, HttpStatus.OK);
    }

    @PutMapping("/users/{id}/role")
    @Transactional
    public ResponseEntity<?> updateUserRole(@PathVariable Long id, @RequestBody Map<String, String> request) {
        if (getAuthenticatedAdmin() == null) {
            return new ResponseEntity<>("Access denied. Admin role required.", HttpStatus.FORBIDDEN);
        }
        String role = request.get("role");
        if (role == null || (!"USER".equals(role) && !"ADMIN".equals(role))) {
            return new ResponseEntity<>("Invalid role. Must be USER or ADMIN.", HttpStatus.BAD_REQUEST);
        }
        Optional<AppUser> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) {
            return new ResponseEntity<>("User not found.", HttpStatus.NOT_FOUND);
        }
        AppUser user = userOpt.get();
        
        // Prevent admin from revoking their own admin access to avoid lockout
        org.springframework.security.core.Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        Long callerId = (Long) ((UsernamePasswordAuthenticationToken) authentication).getDetails();
        if (user.getId().equals(callerId) && !"ADMIN".equals(role)) {
            return new ResponseEntity<>("Cannot downgrade your own role.", HttpStatus.BAD_REQUEST);
        }

        user.setRole(role);
        userRepository.save(user);

        Map<String, Object> map = new HashMap<>();
        map.put("id", user.getId());
        map.put("email", user.getEmail());
        map.put("name", user.getName());
        map.put("provider", user.getProvider());
        map.put("role", user.getRole());
        map.put("subscriptionTier", user.getSubscriptionTier());
        return new ResponseEntity<>(map, HttpStatus.OK);
    }

    @DeleteMapping("/users/{id}")
    @Transactional
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        if (getAuthenticatedAdmin() == null) {
            return new ResponseEntity<>("Access denied. Admin role required.", HttpStatus.FORBIDDEN);
        }
        Optional<AppUser> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) {
            return new ResponseEntity<>("User not found.", HttpStatus.NOT_FOUND);
        }
        AppUser user = userOpt.get();
        
        // Prevent admin from deleting themselves
        org.springframework.security.core.Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        Long callerId = (Long) ((UsernamePasswordAuthenticationToken) authentication).getDetails();
        if (user.getId().equals(callerId)) {
            return new ResponseEntity<>("Cannot delete your own admin account.", HttpStatus.BAD_REQUEST);
        }

        // Delete associated records
        resumeRepository.deleteByUserId(user.getId());
        chatMessageRepository.deleteByUserId(user.getId());
        userRepository.delete(user);
        
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}
