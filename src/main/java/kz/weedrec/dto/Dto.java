package kz.weedrec.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import kz.weedrec.model.Detection;
import kz.weedrec.model.Interaction;
import kz.weedrec.model.Treatment;
import kz.weedrec.model.User;

import java.time.Instant;
import java.util.List;

/** API request and response records. */
public final class Dto {
    private Dto() {}


    public record RegisterRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 6, max = 100) String password,
            @NotBlank String fullName,
            String farmName,
            String region,
            @PositiveOrZero Integer experienceYears,
            String phone,
            /** FARMER (default) or AGRONOMIST; ADMIN cannot self-register */
            User.Role role) {}

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

    public record AuthResponse(String token, String tokenType, long expiresInMs, UserResponse user) {}


    public record UserResponse(String id, String email, User.Role role, User.Profile profile, Instant createdAt) {
        public static UserResponse of(User u) {
            return new UserResponse(u.getId(), u.getEmail(), u.getRole(), u.getProfile(), u.getCreatedAt());
        }
    }

    public record ProfileUpdateRequest(
            @NotBlank String fullName,
            String farmName,
            String region,
            @PositiveOrZero Integer experienceYears,
            String phone) {}


    public record TreatmentRequest(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 5000) String description,
            @NotNull Treatment.Category category,
            String activeIngredient,
            @NotEmpty List<String> targetWeeds,
            @NotEmpty List<String> allowedCrops,
            @Valid DosageDto dosage,
            @NotNull @PositiveOrZero Double pricePerHa) {}

    public record DosageDto(@Positive Double value, String unit) {}

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {}


    public record InteractionRequest(
            @NotBlank String treatmentId,
            @NotNull Interaction.Type type,
            String fieldId,
            String detectionId,
            @Min(1) @Max(5) Integer rating,
            @Min(0) @Max(100) Integer effectiveness) {}


    public record FieldRequest(
            @NotBlank String name,
            @NotBlank String crop,
            @Positive Double areaHa,
            String soilType,
            @DecimalMin("-180") @DecimalMax("180") Double longitude,
            @DecimalMin("-90") @DecimalMax("90") Double latitude) {}

    public record DetectionRequest(
            @NotBlank String fieldId,
            String imageUrl,
            @NotBlank String modelVersion,
            @NotEmpty List<Detection.DetectedWeed> weeds) {}
}
