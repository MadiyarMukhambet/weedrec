package kz.weedrec.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * A user action on a treatment. A separate collection rather than an array in users:
 * it grows without bound and collaborative filtering queries it across users.
 */
@Document(collection = "interactions")
@CompoundIndexes({
        @CompoundIndex(name = "user_time", def = "{'userId': 1, 'timestamp': -1}"),        // user history
        @CompoundIndex(name = "treatment_type", def = "{'treatmentId': 1, 'type': 1}"),   // per-treatment stats
        @CompoundIndex(name = "user_treatment", def = "{'userId': 1, 'treatmentId': 1}")  // user x item matrix
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Interaction {

    @Id
    private String id;

    private String userId;
    private String treatmentId;
    private Type type;
    private String fieldId;       // APPLY / RATING
    private String detectionId;   // detection that led to the action
    private Integer rating;       // 1..5, RATING only
    private Integer effectiveness;// % of weeds killed, optional
    private Instant timestamp;

    public enum Type { VIEW, SAVE, APPLY, RATING }
}
