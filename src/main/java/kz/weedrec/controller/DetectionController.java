package kz.weedrec.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kz.weedrec.dto.Dto.DetectionRequest;
import kz.weedrec.exception.ApiException;
import kz.weedrec.model.Detection;
import kz.weedrec.model.Field;
import kz.weedrec.model.Treatment;
import kz.weedrec.repository.DetectionRepository;
import kz.weedrec.repository.FieldRepository;
import kz.weedrec.repository.WeedRepository;
import kz.weedrec.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/detections")
@RequiredArgsConstructor
@Tag(name = "6. CV model detections")
public class DetectionController {

    private final DetectionRepository detections;
    private final FieldRepository fields;
    private final WeedRepository weeds;
    private final MongoTemplate mongo;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Store a weed detection result for a field image",
            description = "Called by the CV model service (or manually for a demo).")
    public Detection create(@Valid @RequestBody DetectionRequest r) {
        Field f = fields.findById(r.fieldId()).orElseThrow(() -> ApiException.notFound("Field", r.fieldId()));
        if (!f.getUserId().equals(CurrentUser.id())) throw ApiException.notFound("Field", r.fieldId());
        r.weeds().forEach(w -> {
            if (!weeds.existsById(w.getWeedId())) throw ApiException.badRequest("Unknown weed species: " + w.getWeedId());
        });
        return detections.save(Detection.builder()
                .fieldId(f.getId())
                .userId(f.getUserId())
                .imageUrl(r.imageUrl())
                .modelVersion(r.modelVersion())
                .detectedAt(Instant.now())
                .weeds(r.weeds())
                .verifiedByAgronomist(false)
                .build());
    }

    @GetMapping("/{id}/matching-treatments")
    @Operation(summary = "Treatments matching a detection",
            description = "Catalog filter: targets at least one detected weed and is allowed for the field's crop; "
                    + "sorted by rating. This is a baseline lookup; the recommendation algorithm comes in the Final Project.")
    public List<Treatment> matching(@PathVariable String id) {
        Detection d = detections.findById(id).orElseThrow(() -> ApiException.notFound("Detection", id));
        if (!d.getUserId().equals(CurrentUser.id())) throw ApiException.notFound("Detection", id);
        Field f = fields.findById(d.getFieldId()).orElseThrow(() -> ApiException.notFound("Field", d.getFieldId()));

        List<String> weedIds = d.getWeeds().stream().map(Detection.DetectedWeed::getWeedId).toList();
        // served by the multikey indexes on targetWeeds and allowedCrops
        Query q = Query.query(Criteria.where("targetWeeds").in(weedIds).and("allowedCrops").is(f.getCrop()))
                .with(Sort.by(Sort.Direction.DESC, "stats.avgRating")).limit(20);
        return mongo.find(q, Treatment.class);
    }
}
