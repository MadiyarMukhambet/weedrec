package kz.weedrec.service;

import kz.weedrec.dto.Dto.*;
import kz.weedrec.exception.ApiException;
import kz.weedrec.model.Treatment;
import kz.weedrec.repository.InteractionRepository;
import kz.weedrec.repository.TreatmentRepository;
import kz.weedrec.repository.WeedRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.data.mongodb.core.query.TextQuery;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class TreatmentService {

    private final TreatmentRepository treatments;
    private final WeedRepository weeds;
    private final InteractionRepository interactions;
    private final MongoTemplate mongo;

    /** Catalog search parameters. All optional, combined with AND. */
    public record SearchParams(
            String q,              // full-text over name + description
            String name,           // case-insensitive substring of the name
            Treatment.Category category,
            String weed,           // weed id, e.g. avena_fatua
            String crop,           // crop, e.g. wheat
            Double minPrice,
            Double maxPrice,
            Double minRating,
            String sort,           // relevance | price_asc | price_desc | rating | popular | name
            int page,
            int size) {}

    public PageResponse<Treatment> search(SearchParams p) {
        List<Criteria> filters = new ArrayList<>();
        if (p.name() != null && !p.name().isBlank())
            filters.add(Criteria.where("name").regex(Pattern.quote(p.name().trim()), "i"));
        if (p.category() != null) filters.add(Criteria.where("category").is(p.category()));
        if (p.weed() != null && !p.weed().isBlank()) filters.add(Criteria.where("targetWeeds").is(p.weed()));
        if (p.crop() != null && !p.crop().isBlank()) filters.add(Criteria.where("allowedCrops").is(p.crop().trim().toLowerCase()));
        if (p.minPrice() != null || p.maxPrice() != null) {
            Criteria price = Criteria.where("pricePerHa");
            if (p.minPrice() != null) price.gte(p.minPrice());
            if (p.maxPrice() != null) price.lte(p.maxPrice());
            filters.add(price);
        }
        if (p.minRating() != null) filters.add(Criteria.where("stats.avgRating").gte(p.minRating()));

        boolean fullText = p.q() != null && !p.q().isBlank();
        Sort sort = switch (p.sort() == null ? "" : p.sort()) {
            case "price_asc" -> Sort.by("pricePerHa").ascending();
            case "price_desc" -> Sort.by("pricePerHa").descending();
            case "rating" -> Sort.by("stats.avgRating").descending();
            case "popular" -> Sort.by("stats.applications").descending();
            case "name" -> Sort.by("name").ascending();
            default -> fullText ? Sort.unsorted() : Sort.by("name").ascending(); // relevance = textScore
        };

        Query query;
        if (fullText) {
            TextQuery tq = TextQuery.queryText(TextCriteria.forLanguage("english").matching(p.q().trim()));
            tq.includeScore();
            if (sort.isUnsorted()) tq.sortByScore();   // relevance unless another sort was requested
            query = tq;
        } else {
            query = new Query();
        }
        if (!filters.isEmpty()) query.addCriteria(new Criteria().andOperator(filters));

        long total = mongo.count(Query.of(query).limit(-1).skip(-1), Treatment.class);
        int size = Math.max(1, Math.min(p.size(), 100));
        query.with(sort).skip((long) p.page() * size).limit(size);

        List<Treatment> content = mongo.find(query, Treatment.class);
        return new PageResponse<>(content, p.page(), size, total, (int) Math.ceil((double) total / size));
    }

    public Treatment get(String id) {
        return treatments.findById(id).orElseThrow(() -> ApiException.notFound("Treatment", id));
    }

    public Treatment create(TreatmentRequest r) {
        Treatment t = new Treatment();
        apply(t, r);
        return treatments.save(t);
    }

    public Treatment update(String id, TreatmentRequest r) {
        Treatment t = get(id);
        apply(t, r);                 // stats are derived from interactions, left untouched
        return treatments.save(t);
    }

    public void delete(String id) {
        if (!treatments.existsById(id)) throw ApiException.notFound("Treatment", id);
        treatments.deleteById(id);
        interactions.deleteByTreatmentId(id);   // no dangling references
    }

    private void apply(Treatment t, TreatmentRequest r) {
        List<String> unknown = r.targetWeeds().stream().filter(w -> !weeds.existsById(w)).toList();
        if (!unknown.isEmpty()) throw ApiException.badRequest("Unknown weed species: " + unknown);

        t.setName(r.name().trim());
        t.setDescription(r.description().trim());
        t.setCategory(r.category());
        t.setActiveIngredient(r.activeIngredient());
        t.setTargetWeeds(r.targetWeeds());
        t.setAllowedCrops(r.allowedCrops().stream().map(c -> c.trim().toLowerCase()).toList());
        t.setDosage(r.dosage() == null ? null : new Treatment.Dosage(r.dosage().value(), r.dosage().unit()));
        t.setPricePerHa(r.pricePerHa());
        if (t.getStats() == null) t.setStats(new Treatment.Stats());
    }
}
