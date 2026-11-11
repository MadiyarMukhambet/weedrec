package kz.weedrec.service;

import kz.weedrec.dto.Dto.InteractionRequest;
import kz.weedrec.exception.ApiException;
import kz.weedrec.model.Field;
import kz.weedrec.model.Interaction;
import kz.weedrec.model.Treatment;
import kz.weedrec.repository.FieldRepository;
import kz.weedrec.repository.InteractionRepository;
import kz.weedrec.repository.TreatmentRepository;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class InteractionService {

    private final InteractionRepository interactions;
    private final TreatmentRepository treatments;
    private final FieldRepository fields;
    private final MongoTemplate mongo;

    public Interaction record(String userId, InteractionRequest r) {
        if (!treatments.existsById(r.treatmentId())) throw ApiException.notFound("Treatment", r.treatmentId());

        if (r.type() == Interaction.Type.RATING && r.rating() == null)
            throw ApiException.badRequest("rating (1-5) is required for type=RATING");
        if ((r.type() == Interaction.Type.APPLY || r.type() == Interaction.Type.RATING) && r.fieldId() != null) {
            Field f = fields.findById(r.fieldId()).orElseThrow(() -> ApiException.notFound("Field", r.fieldId()));
            if (!f.getUserId().equals(userId)) throw ApiException.badRequest("The field belongs to another user");
        }

        Interaction saved;
        if (r.type() == Interaction.Type.RATING) {
            // one rating per (user, treatment): a new rating replaces the old one
            Interaction existing = mongo.findOne(Query.query(Criteria.where("userId").is(userId)
                    .and("treatmentId").is(r.treatmentId()).and("type").is(Interaction.Type.RATING)), Interaction.class);
            Interaction i = existing != null ? existing : Interaction.builder()
                    .userId(userId).treatmentId(r.treatmentId()).type(Interaction.Type.RATING).build();
            i.setRating(r.rating());
            i.setEffectiveness(r.effectiveness());
            i.setFieldId(r.fieldId());
            i.setDetectionId(r.detectionId());
            i.setTimestamp(Instant.now());
            saved = interactions.save(i);
            recomputeRating(r.treatmentId());
        } else {
            saved = interactions.save(Interaction.builder()
                    .userId(userId).treatmentId(r.treatmentId()).type(r.type())
                    .fieldId(r.fieldId()).detectionId(r.detectionId())
                    .effectiveness(r.effectiveness())
                    .timestamp(Instant.now()).build());
            incrementCounter(r.treatmentId(), r.type());
        }
        return saved;
    }

    /** Atomic $inc of the denormalized counters on the treatment. */
    public void incrementCounter(String treatmentId, Interaction.Type type) {
        String field = switch (type) {
            case VIEW -> "stats.views";
            case APPLY -> "stats.applications";
            default -> null;
        };
        if (field != null)
            mongo.updateFirst(Query.query(Criteria.where("_id").is(treatmentId)), new Update().inc(field, 1), Treatment.class);
    }

    /** Recomputes the average rating by aggregation (index {treatmentId: 1, type: 1}). */
    public void recomputeRating(String treatmentId) {
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("treatmentId").is(treatmentId).and("type").is(Interaction.Type.RATING)),
                Aggregation.group("treatmentId").avg("rating").as("avg").count().as("cnt"));
        Document res = mongo.aggregate(agg, Interaction.class, Document.class).getUniqueMappedResult();
        double avg = res == null ? 0 : Math.round(((Number) res.get("avg")).doubleValue() * 100) / 100.0;
        long cnt = res == null ? 0 : ((Number) res.get("cnt")).longValue();
        mongo.updateFirst(Query.query(Criteria.where("_id").is(treatmentId)),
                new Update().set("stats.avgRating", avg).set("stats.ratingsCount", cnt), Treatment.class);
    }
}
