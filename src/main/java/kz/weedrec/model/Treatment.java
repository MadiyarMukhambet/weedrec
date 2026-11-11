package kz.weedrec.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.TextScore;

import java.util.List;

/** Catalog item: a weed-control product or method. */
@Document(collection = "treatments", language = "english")
@CompoundIndex(name = "category_price", def = "{'category': 1, 'pricePerHa': 1}")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Treatment {

    @Id
    private String id;

    @TextIndexed(weight = 3)          // name outweighs description
    private String name;

    @TextIndexed
    private String description;

    private Category category;
    private String activeIngredient;

    @Indexed
    private List<String> targetWeeds;  // ids from weeds
    @Indexed
    private List<String> allowedCrops;

    private Dosage dosage;
    private Double pricePerHa;         // KZT per hectare

    @Builder.Default
    private Stats stats = new Stats(); // denormalized so catalog reads need no aggregation

    @TextScore
    private Float score;               // set only by full-text search

    public enum Category { CHEMICAL, MECHANICAL, BIOLOGICAL, AGROTECHNICAL }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Dosage {
        private Double value;
        private String unit;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Stats {
        private double avgRating;
        private long ratingsCount;
        private long applications;
        private long views;
    }
}
