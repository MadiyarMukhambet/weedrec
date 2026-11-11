package kz.weedrec.repository;

import kz.weedrec.model.Detection;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface DetectionRepository extends MongoRepository<Detection, String> {
    // index {fieldId: 1, detectedAt: -1}
    List<Detection> findByFieldIdOrderByDetectedAtDesc(String fieldId, Pageable pageable);
}
