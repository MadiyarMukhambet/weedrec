package kz.weedrec.repository;

import kz.weedrec.model.Treatment;
import org.springframework.data.mongodb.repository.MongoRepository;

/** Filtered search lives in TreatmentService (MongoTemplate). */
public interface TreatmentRepository extends MongoRepository<Treatment, String> {
}
