package kz.weedrec.repository;

import kz.weedrec.model.Field;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface FieldRepository extends MongoRepository<Field, String> {
    List<Field> findByUserId(String userId);
}
