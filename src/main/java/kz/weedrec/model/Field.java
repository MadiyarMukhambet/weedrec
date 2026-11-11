package kz.weedrec.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexType;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexed;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** A user's field. Stored separately (detections reference it) and points to its owner. */
@Document(collection = "fields")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Field {

    @Id
    private String id;

    @Indexed
    private String userId;

    private String name;
    private String crop;
    private Double areaHa;
    private String soilType;

    @GeoSpatialIndexed(type = GeoSpatialIndexType.GEO_2DSPHERE)   // nearby farms
    private GeoJsonPoint location;
}
