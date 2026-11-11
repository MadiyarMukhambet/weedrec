package kz.weedrec.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/**
 * CV model output for one field image.
 * Detected weeds are embedded: there are few of them, they are always read together and never change.
 */
@Document(collection = "detections")
@CompoundIndex(name = "field_date", def = "{'fieldId': 1, 'detectedAt': -1}")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Detection {

    @Id
    private String id;

    private String fieldId;
    private String userId;
    private String imageUrl;
    private String modelVersion;
    private Instant detectedAt;
    private List<DetectedWeed> weeds;
    private boolean verifiedByAgronomist;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DetectedWeed {
        private String weedId;
        private double confidence;  // 0..1
        private double density;     // plants per m²
    }
}
