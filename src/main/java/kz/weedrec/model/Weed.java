package kz.weedrec.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Weed species. They match the CV model classes,
 * so _id is the readable class code (e.g. "avena_fatua").
 */
@Document(collection = "weeds")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Weed {

    @Id
    private String id;

    private String name;
    private String latinName;
    private String type;        // monocot | dicot
    private String lifecycle;   // annual | perennial
}
