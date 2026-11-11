package kz.weedrec.repository;

import kz.weedrec.model.Interaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface InteractionRepository extends MongoRepository<Interaction, String> {
    // index {userId: 1, timestamp: -1}
    Page<Interaction> findByUserIdOrderByTimestampDesc(String userId, Pageable pageable);

    Page<Interaction> findByUserIdAndTypeOrderByTimestampDesc(String userId, Interaction.Type type, Pageable pageable);

    // index {treatmentId: 1, type: 1}
    List<Interaction> findByTreatmentIdAndType(String treatmentId, Interaction.Type type);

    void deleteByTreatmentId(String treatmentId);
}
