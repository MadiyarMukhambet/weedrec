package kz.weedrec.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kz.weedrec.dto.Dto.*;
import kz.weedrec.model.Interaction;
import kz.weedrec.security.CurrentUser;
import kz.weedrec.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
@Tag(name = "2. Profile and history")
public class UserController {

    private final UserService users;

    @GetMapping
    @Operation(summary = "Current user and profile")
    public UserResponse me() {
        return users.get(CurrentUser.id());
    }

    @PutMapping("/profile")
    @Operation(summary = "Update profile (name, farm, region, experience, phone)")
    public UserResponse updateProfile(@Valid @RequestBody ProfileUpdateRequest r) {
        return users.updateProfile(CurrentUser.id(), r);
    }

    @GetMapping("/history")
    @Operation(summary = "Interaction history (newest first), optionally filtered by type")
    public PageResponse<Interaction> history(@RequestParam(required = false) Interaction.Type type,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return users.history(CurrentUser.id(), type, page, size);
    }
}
