package kz.weedrec.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kz.weedrec.dto.Dto.*;
import kz.weedrec.model.Interaction;
import kz.weedrec.model.Treatment;
import kz.weedrec.security.CurrentUser;
import kz.weedrec.service.InteractionService;
import kz.weedrec.service.TreatmentService;
import kz.weedrec.service.TreatmentService.SearchParams;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/treatments")
@RequiredArgsConstructor
@Tag(name = "3. Treatment catalog and search")
public class TreatmentController {

    private final TreatmentService treatments;
    private final InteractionService interactions;

    @GetMapping
    @Operation(summary = "List and search treatments",
            description = "All parameters are optional and combined with AND. q is full-text search over name and description; "
                    + "name is a substring of the name; weed is a weed id; crop is a crop name.")
    public PageResponse<Treatment> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Treatment.Category category,
            @RequestParam(required = false) String weed,
            @RequestParam(required = false) String crop,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(required = false) Double minRating,
            @Parameter(description = "relevance | price_asc | price_desc | rating | popular | name")
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return treatments.search(new SearchParams(q, name, category, weed, crop,
                minPrice, maxPrice, minRating, sort, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a treatment (records a VIEW for a logged-in user)")
    public Treatment get(@PathVariable String id) {
        Treatment t = treatments.get(id);
        CurrentUser.idIfPresent().ifPresent(uid -> interactions.record(uid,
                new InteractionRequest(id, Interaction.Type.VIEW, null, null, null, null)));
        return t;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','AGRONOMIST')")
    @Operation(summary = "Create a treatment (ADMIN, AGRONOMIST)")
    public Treatment create(@Valid @RequestBody TreatmentRequest r) {
        return treatments.create(r);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','AGRONOMIST')")
    @Operation(summary = "Update a treatment (ADMIN, AGRONOMIST)")
    public Treatment update(@PathVariable String id, @Valid @RequestBody TreatmentRequest r) {
        return treatments.update(id, r);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a treatment and its interactions (ADMIN)")
    public void delete(@PathVariable String id) {
        treatments.delete(id);
    }
}
