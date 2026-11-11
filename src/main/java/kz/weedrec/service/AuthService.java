package kz.weedrec.service;

import kz.weedrec.dto.Dto.*;
import kz.weedrec.exception.ApiException;
import kz.weedrec.model.User;
import kz.weedrec.repository.UserRepository;
import kz.weedrec.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthResponse register(RegisterRequest r) {
        String email = r.email().trim().toLowerCase();
        if (users.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "A user with this email already exists");
        }
        User.Role role = r.role() == User.Role.AGRONOMIST ? User.Role.AGRONOMIST : User.Role.FARMER;

        User user = users.save(User.builder()
                .email(email)
                .passwordHash(encoder.encode(r.password()))
                .role(role)
                .profile(User.Profile.builder()
                        .fullName(r.fullName())
                        .farmName(r.farmName())
                        .region(r.region())
                        .experienceYears(r.experienceYears())
                        .phone(r.phone())
                        .build())
                .createdAt(Instant.now())
                .build());
        return response(user);
    }

    public AuthResponse login(LoginRequest r) {
        User user = users.findByEmail(r.email().trim().toLowerCase())
                .filter(u -> encoder.matches(r.password(), u.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
        return response(user);
    }

    private AuthResponse response(User user) {
        return new AuthResponse(jwt.generate(user), "Bearer", jwt.getExpirationMs(), UserResponse.of(user));
    }
}
