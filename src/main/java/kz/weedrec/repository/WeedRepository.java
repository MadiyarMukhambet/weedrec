package kz.weedrec.repository;

import kz.weedrec.model.Weed;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface WeedRepository extends MongoRepository<Weed, String> {
}
