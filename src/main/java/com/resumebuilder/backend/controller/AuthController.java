package com.resumebuilder.backend.controller;

import com.resumebuilder.backend.model.AppUser;
import com.resumebuilder.backend.repository.UserRepository;
import com.resumebuilder.backend.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final com.resumebuilder.backend.service.EmailService emailService;
    private final RestTemplate restTemplate = new RestTemplate();

    @Autowired
    public AuthController(UserRepository userRepository, 
                          JwtUtil jwtUtil, 
                          PasswordEncoder passwordEncoder,
                          com.resumebuilder.backend.service.EmailService emailService) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    private String generateOtp() {
        java.util.Random random = new java.util.Random();
        int num = 100000 + random.nextInt(900000);
        return String.valueOf(num);
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String name = request.get("name");
        String password = request.get("password");
        String confirmPassword = request.get("confirmPassword");

        if (email == null || email.trim().isEmpty() || 
            name == null || name.trim().isEmpty() || 
            password == null || password.trim().isEmpty() || 
            confirmPassword == null || confirmPassword.trim().isEmpty()) {
            return new ResponseEntity<>("All fields (name, email, password, confirm password) are required.", HttpStatus.BAD_REQUEST);
        }

        if (!password.equals(confirmPassword)) {
            return new ResponseEntity<>("Passwords do not match.", HttpStatus.BAD_REQUEST);
        }

        String cleanedEmail = email.trim().toLowerCase();
        Optional<AppUser> existingUser = userRepository.findByEmail(cleanedEmail);
        if (existingUser.isPresent()) {
            AppUser user = existingUser.get();
            if (user.getVerified()) {
                return new ResponseEntity<>("Email address is already registered.", HttpStatus.BAD_REQUEST);
            } else {
                userRepository.delete(user);
            }
        }

        String otp = generateOtp();
        AppUser newUser = AppUser.builder()
                .email(cleanedEmail)
                .name(name.trim())
                .password(passwordEncoder.encode(password))
                .provider("LOCAL")
                .verified(false)
                .otp(otp)
                .otpExpiry(java.time.LocalDateTime.now().plusMinutes(5))
                .build();

        AppUser savedUser = userRepository.save(newUser);
        emailService.sendOtpEmail(savedUser.getEmail(), otp, "Account Verification");
        
        Map<String, Object> responsePayload = new HashMap<>();
        responsePayload.put("status", "PENDING_VERIFICATION");
        responsePayload.put("email", savedUser.getEmail());
        responsePayload.put("message", "Registration successful. A verification OTP has been sent to your email.");

        return new ResponseEntity<>(responsePayload, HttpStatus.OK);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String password = request.get("password");

        if (email == null || password == null) {
            return new ResponseEntity<>("Email and password are required.", HttpStatus.BAD_REQUEST);
        }

        Optional<AppUser> userOpt = userRepository.findByEmail(email.trim().toLowerCase());
        if (userOpt.isEmpty()) {
            return new ResponseEntity<>("Invalid email or password.", HttpStatus.UNAUTHORIZED);
        }

        AppUser user = userOpt.get();
        if (!"LOCAL".equals(user.getProvider()) || !passwordEncoder.matches(password, user.getPassword())) {
            return new ResponseEntity<>("Invalid email or password.", HttpStatus.UNAUTHORIZED);
        }

        if (!user.getVerified()) {
            String otp = generateOtp();
            user.setOtp(otp);
            user.setOtpExpiry(java.time.LocalDateTime.now().plusMinutes(5));
            userRepository.save(user);

            emailService.sendOtpEmail(user.getEmail(), otp, "Account Verification");

            Map<String, Object> responsePayload = new HashMap<>();
            responsePayload.put("status", "PENDING_VERIFICATION");
            responsePayload.put("email", user.getEmail());
            responsePayload.put("message", "Account is unverified. A verification OTP has been sent to your email.");
            return new ResponseEntity<>(responsePayload, HttpStatus.OK);
        }

        // Generate backend JWT token
        String jwtToken = jwtUtil.generateToken(user.getEmail(), user.getId(), user.getRole());

        Map<String, Object> responsePayload = new HashMap<>();
        responsePayload.put("id", user.getId());
        responsePayload.put("email", user.getEmail());
        responsePayload.put("name", user.getName());
        responsePayload.put("provider", user.getProvider());
        responsePayload.put("role", user.getRole());
        responsePayload.put("subscriptionTier", user.getSubscriptionTier());
        responsePayload.put("token", jwtToken);

        return new ResponseEntity<>(responsePayload, HttpStatus.OK);
    }

    @PostMapping("/google")
    public ResponseEntity<?> googleLogin(@RequestBody Map<String, String> request) {
        // Accept both idToken or credential parameter from React OAuth response
        String idToken = request.get("idToken");
        if (idToken == null) {
            idToken = request.get("credential");
        }

        // Support direct local developer mock login mode
        String mockEmail = request.get("email");
        if (idToken == null && mockEmail != null) {
            return processGoogleUser(mockEmail, request.get("name"));
        }

        if (idToken == null || idToken.trim().isEmpty()) {
            return new ResponseEntity<>("Google ID Token is required.", HttpStatus.BAD_REQUEST);
        }

        try {
            // BACKEND VERIFICATION: Query Google's public OAuth verification API
            String googleVerificationUrl = "https://oauth2.googleapis.com/tokeninfo?id_token=" + idToken;
            @SuppressWarnings("rawtypes")
            ResponseEntity<Map> googleResponse = restTemplate.getForEntity(googleVerificationUrl, Map.class);
            
            if (googleResponse.getStatusCode() != HttpStatus.OK || googleResponse.getBody() == null) {
                return new ResponseEntity<>("Failed to verify Google ID token with Google servers.", HttpStatus.UNAUTHORIZED);
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> profile = googleResponse.getBody();
            String email = (String) profile.get("email");
            String name = (String) profile.get("name");

            if (email == null) {
                return new ResponseEntity<>("Google profile does not contain an email address.", HttpStatus.BAD_REQUEST);
            }

            return processGoogleUser(email, name);
        } catch (Exception e) {
            logger.error("Google token verification failed: {}", e.getMessage());
            return new ResponseEntity<>("Google token verification failed: " + e.getMessage(), HttpStatus.UNAUTHORIZED);
        }
    }

    private ResponseEntity<?> processGoogleUser(String email, String name) {
        String cleanedEmail = email.trim().toLowerCase();
        Optional<AppUser> userOpt = userRepository.findByEmail(cleanedEmail);
        
        AppUser user;
        if (userOpt.isEmpty()) {
            // Auto sign up Google account
            AppUser newUser = AppUser.builder()
                    .email(cleanedEmail)
                    .name(name != null && !name.trim().isEmpty() ? name.trim() : cleanedEmail.split("@")[0])
                    .provider("GOOGLE")
                    .build();
            user = userRepository.save(newUser);
        } else {
            user = userOpt.get();
        }

        // Generate backend JWT token
        String jwtToken = jwtUtil.generateToken(user.getEmail(), user.getId(), user.getRole());

        Map<String, Object> responsePayload = new HashMap<>();
        responsePayload.put("id", user.getId());
        responsePayload.put("email", user.getEmail());
        responsePayload.put("name", user.getName());
        responsePayload.put("provider", user.getProvider());
        responsePayload.put("role", user.getRole());
        responsePayload.put("subscriptionTier", user.getSubscriptionTier());
        responsePayload.put("token", jwtToken);

        return new ResponseEntity<>(responsePayload, HttpStatus.OK);
    }

    @GetMapping("/me")
    public ResponseEntity<?> getMe(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        org.springframework.security.core.Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication instanceof org.springframework.security.authentication.UsernamePasswordAuthenticationToken)) {
            return new ResponseEntity<>("Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        Long userId = (Long) ((org.springframework.security.authentication.UsernamePasswordAuthenticationToken) authentication).getDetails();
        if (userId == null) {
            return new ResponseEntity<>("Unauthorized", HttpStatus.UNAUTHORIZED);
        }

        Optional<AppUser> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return new ResponseEntity<>("User not found", HttpStatus.NOT_FOUND);
        }

        AppUser user = userOpt.get();
        Map<String, Object> responsePayload = new HashMap<>();
        responsePayload.put("id", user.getId());
        responsePayload.put("email", user.getEmail());
        responsePayload.put("name", user.getName());
        responsePayload.put("provider", user.getProvider());
        responsePayload.put("role", user.getRole());
        responsePayload.put("subscriptionTier", user.getSubscriptionTier());

        return new ResponseEntity<>(responsePayload, HttpStatus.OK);
    }

    @PostMapping("/verify-signup")
    public ResponseEntity<?> verifySignup(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String otp = request.get("otp");

        if (email == null || otp == null) {
            return new ResponseEntity<>("Email and OTP are required.", HttpStatus.BAD_REQUEST);
        }

        Optional<AppUser> userOpt = userRepository.findByEmail(email.trim().toLowerCase());
        if (userOpt.isEmpty()) {
            return new ResponseEntity<>("User not found.", HttpStatus.NOT_FOUND);
        }

        AppUser user = userOpt.get();
        if (user.getVerified()) {
            return new ResponseEntity<>("Account is already verified.", HttpStatus.BAD_REQUEST);
        }

        if (user.getOtp() == null || !user.getOtp().equals(otp.trim())) {
            return new ResponseEntity<>("Invalid verification code.", HttpStatus.BAD_REQUEST);
        }

        if (user.getOtpExpiry() == null || user.getOtpExpiry().isBefore(java.time.LocalDateTime.now())) {
            return new ResponseEntity<>("Verification code has expired. Please signup again.", HttpStatus.BAD_REQUEST);
        }

        user.setVerified(true);
        user.setOtp(null);
        user.setOtpExpiry(null);
        AppUser savedUser = userRepository.save(user);

        String jwtToken = jwtUtil.generateToken(savedUser.getEmail(), savedUser.getId(), savedUser.getRole());

        Map<String, Object> responsePayload = new HashMap<>();
        responsePayload.put("id", savedUser.getId());
        responsePayload.put("email", savedUser.getEmail());
        responsePayload.put("name", savedUser.getName());
        responsePayload.put("provider", savedUser.getProvider());
        responsePayload.put("role", savedUser.getRole());
        responsePayload.put("subscriptionTier", savedUser.getSubscriptionTier());
        responsePayload.put("token", jwtToken);

        return new ResponseEntity<>(responsePayload, HttpStatus.OK);
    }


    @PostMapping("/login-otp-request")
    public ResponseEntity<?> loginOtpRequest(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        if (email == null || email.trim().isEmpty()) {
            return new ResponseEntity<>("Email is required.", HttpStatus.BAD_REQUEST);
        }

        Optional<AppUser> userOpt = userRepository.findByEmail(email.trim().toLowerCase());
        if (userOpt.isEmpty()) {
            return new ResponseEntity<>("No account found with this email.", HttpStatus.BAD_REQUEST);
        }

        AppUser user = userOpt.get();
        if (!"LOCAL".equals(user.getProvider())) {
            return new ResponseEntity<>("Account was registered via " + user.getProvider() + ". Please sign in using OAuth.", HttpStatus.BAD_REQUEST);
        }

        String otp = generateOtp();
        user.setOtp(otp);
        user.setOtpExpiry(java.time.LocalDateTime.now().plusMinutes(5));
        userRepository.save(user);

        emailService.sendOtpEmail(user.getEmail(), otp, "Login Authentication");

        return new ResponseEntity<>(Map.of("message", "OTP sent to your email."), HttpStatus.OK);
    }

    @PostMapping("/login-otp-verify")
    public ResponseEntity<?> loginOtpVerify(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String otp = request.get("otp");

        if (email == null || otp == null) {
            return new ResponseEntity<>("Email and OTP are required.", HttpStatus.BAD_REQUEST);
        }

        Optional<AppUser> userOpt = userRepository.findByEmail(email.trim().toLowerCase());
        if (userOpt.isEmpty()) {
            return new ResponseEntity<>("User not found.", HttpStatus.NOT_FOUND);
        }

        AppUser user = userOpt.get();
        if (user.getOtp() == null || !user.getOtp().equals(otp.trim())) {
            return new ResponseEntity<>("Invalid verification code.", HttpStatus.BAD_REQUEST);
        }

        if (user.getOtpExpiry() == null || user.getOtpExpiry().isBefore(java.time.LocalDateTime.now())) {
            return new ResponseEntity<>("Verification code has expired. Please request a new one.", HttpStatus.BAD_REQUEST);
        }

        user.setVerified(true);
        user.setOtp(null);
        user.setOtpExpiry(null);
        AppUser savedUser = userRepository.save(user);

        String jwtToken = jwtUtil.generateToken(savedUser.getEmail(), savedUser.getId(), savedUser.getRole());

        Map<String, Object> responsePayload = new HashMap<>();
        responsePayload.put("id", savedUser.getId());
        responsePayload.put("email", savedUser.getEmail());
        responsePayload.put("name", savedUser.getName());
        responsePayload.put("provider", savedUser.getProvider());
        responsePayload.put("role", savedUser.getRole());
        responsePayload.put("subscriptionTier", savedUser.getSubscriptionTier());
        responsePayload.put("token", jwtToken);

        return new ResponseEntity<>(responsePayload, HttpStatus.OK);
    }

    @PostMapping("/forgot-password-request")
    public ResponseEntity<?> forgotPasswordRequest(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        if (email == null || email.trim().isEmpty()) {
            return new ResponseEntity<>("Email is required.", HttpStatus.BAD_REQUEST);
        }

        Optional<AppUser> userOpt = userRepository.findByEmail(email.trim().toLowerCase());
        if (userOpt.isEmpty()) {
            return new ResponseEntity<>("No account found with this email.", HttpStatus.BAD_REQUEST);
        }

        AppUser user = userOpt.get();
        if (!"LOCAL".equals(user.getProvider()) && !"GOOGLE".equals(user.getProvider())) {
            return new ResponseEntity<>("Account is registered via " + user.getProvider() + ". Password resets are unavailable.", HttpStatus.BAD_REQUEST);
        }

        String otp = generateOtp();
        user.setOtp(otp);
        user.setOtpExpiry(java.time.LocalDateTime.now().plusMinutes(5));
        userRepository.save(user);

        emailService.sendOtpEmail(user.getEmail(), otp, "Password Reset");

        return new ResponseEntity<>(Map.of("message", "Password reset OTP sent to your email."), HttpStatus.OK);
    }

    @PostMapping("/forgot-password-reset")
    public ResponseEntity<?> forgotPasswordReset(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String otp = request.get("otp");
        String newPassword = request.get("newPassword");

        if (email == null || otp == null || newPassword == null || newPassword.trim().isEmpty()) {
            return new ResponseEntity<>("All fields (email, otp, and newPassword) are required.", HttpStatus.BAD_REQUEST);
        }

        Optional<AppUser> userOpt = userRepository.findByEmail(email.trim().toLowerCase());
        if (userOpt.isEmpty()) {
            return new ResponseEntity<>("User not found.", HttpStatus.NOT_FOUND);
        }

        AppUser user = userOpt.get();
        if (user.getOtp() == null || !user.getOtp().equals(otp.trim())) {
            return new ResponseEntity<>("Invalid verification code.", HttpStatus.BAD_REQUEST);
        }

        if (user.getOtpExpiry() == null || user.getOtpExpiry().isBefore(java.time.LocalDateTime.now())) {
            return new ResponseEntity<>("Verification code has expired. Please request a new reset code.", HttpStatus.BAD_REQUEST);
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setProvider("LOCAL");
        user.setOtp(null);
        user.setOtpExpiry(null);
        userRepository.save(user);

        return new ResponseEntity<>(Map.of("message", "Password reset successfully. Please log in with your new password."), HttpStatus.OK);
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                           @RequestBody Map<String, String> payload) {
        org.springframework.security.core.Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication instanceof org.springframework.security.authentication.UsernamePasswordAuthenticationToken)) {
            return new ResponseEntity<>("Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        Long userId = (Long) ((org.springframework.security.authentication.UsernamePasswordAuthenticationToken) authentication).getDetails();
        if (userId == null) {
            return new ResponseEntity<>("Unauthorized", HttpStatus.UNAUTHORIZED);
        }

        Optional<AppUser> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return new ResponseEntity<>("User not found", HttpStatus.NOT_FOUND);
        }

        AppUser user = userOpt.get();
        
        if (payload.containsKey("name")) {
            user.setName(payload.get("name"));
        }

        if (payload.containsKey("newPassword")) {
            String newPassword = payload.get("newPassword");
            if (newPassword == null || newPassword.trim().isEmpty()) {
                return new ResponseEntity<>("New password cannot be empty.", HttpStatus.BAD_REQUEST);
            }

            if ("LOCAL".equalsIgnoreCase(user.getProvider()) && user.getPassword() != null && !user.getPassword().isEmpty()) {
                String oldPassword = payload.get("oldPassword");
                if (oldPassword == null || oldPassword.trim().isEmpty()) {
                    return new ResponseEntity<>("Current password is required to change password.", HttpStatus.BAD_REQUEST);
                }
                if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
                    return new ResponseEntity<>("Current password does not match database record.", HttpStatus.BAD_REQUEST);
                }
            }

            user.setPassword(passwordEncoder.encode(newPassword));
            user.setProvider("LOCAL");
        }

        userRepository.save(user);
        logger.info("User profile updated for user id: {}", userId);

        Map<String, Object> responsePayload = new HashMap<>();
        responsePayload.put("id", user.getId());
        responsePayload.put("email", user.getEmail());
        responsePayload.put("name", user.getName());
        responsePayload.put("provider", user.getProvider());
        responsePayload.put("role", user.getRole());
        responsePayload.put("subscriptionTier", user.getSubscriptionTier());

        return new ResponseEntity<>(responsePayload, HttpStatus.OK);
    }

    @PostMapping("/profile/verify-password")
    public ResponseEntity<?> verifyPassword(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                            @RequestBody Map<String, String> payload) {
        org.springframework.security.core.Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication instanceof org.springframework.security.authentication.UsernamePasswordAuthenticationToken)) {
            return new ResponseEntity<>("Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        Long userId = (Long) ((org.springframework.security.authentication.UsernamePasswordAuthenticationToken) authentication).getDetails();
        if (userId == null) {
            return new ResponseEntity<>("Unauthorized", HttpStatus.UNAUTHORIZED);
        }

        Optional<AppUser> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return new ResponseEntity<>("User not found", HttpStatus.NOT_FOUND);
        }

        AppUser user = userOpt.get();
        String password = payload.get("password");

        if (password == null || password.trim().isEmpty()) {
            return new ResponseEntity<>(Map.of("valid", false, "message", "Password is required"), HttpStatus.BAD_REQUEST);
        }

        boolean matches = passwordEncoder.matches(password, user.getPassword());
        if (!matches) {
            return new ResponseEntity<>(Map.of("valid", false, "message", "Incorrect password"), HttpStatus.BAD_REQUEST);
        }

        return new ResponseEntity<>(Map.of("valid", true), HttpStatus.OK);
    }
}
