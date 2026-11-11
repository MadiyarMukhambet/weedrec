package kz.weedrec.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** Platform user: farmer, agronomist or admin. The profile is embedded. */
@Document(collection = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    private String id;

    @Indexed(unique = true)
    private String email;

    private String passwordHash;

    private Role role;

    private Profile profile;          // 1:1, always read with the user

    private Instant createdAt;

    public enum Role { FARMER, AGRONOMIST, ADMIN }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Profile {
        private String fullName;
        private String farmName;
        private String region;
        private Integer experienceYears;
        private String phone;
    }
}
