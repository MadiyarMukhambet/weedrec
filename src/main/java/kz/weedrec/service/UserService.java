package kz.weedrec.service;

import kz.weedrec.dto.Dto.*;
import kz.weedrec.exception.ApiException;
import kz.weedrec.model.Interaction;
import kz.weedrec.model.User;
import kz.weedrec.repository.InteractionRepository;
import kz.weedrec.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository users;
    private final InteractionRepository interactions;

    public UserResponse get(String userId) {
        return UserResponse.of(find(userId));
    }

    public UserResponse updateProfile(String userId, ProfileUpdateRequest r) {
        User u = find(userId);
        u.setProfile(User.Profile.builder()
                .fullName(r.fullName())
                .farmName(r.farmName())
                .region(r.region())
                .experienceYears(r.experienceYears())
                .phone(r.phone())
                .build());
        return UserResponse.of(users.save(u));
    }

    /** Interaction history, newest first (index {userId: 1, timestamp: -1}). */
    public PageResponse<Interaction> history(String userId, Interaction.Type type, int page, int size) {
        PageRequest pr = PageRequest.of(page, Math.min(size, 100));
        Page<Interaction> p = (type == null)
                ? interactions.findByUserIdOrderByTimestampDesc(userId, pr)
                : interactions.findByUserIdAndTypeOrderByTimestampDesc(userId, type, pr);
        return new PageResponse<>(p.getContent(), page, pr.getPageSize(), p.getTotalElements(), p.getTotalPages());
    }

    private User find(String id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("User", id));
    }
}
