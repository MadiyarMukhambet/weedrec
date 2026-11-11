package kz.weedrec.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kz.weedrec.dto.Dto.InteractionRequest;
import kz.weedrec.model.Interaction;
import kz.weedrec.security.CurrentUser;
import kz.weedrec.service.InteractionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/interactions")
@RequiredArgsConstructor
@Tag(name = "4. Interactions")
public class InteractionController {

    private final InteractionService interactions;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record an interaction: VIEW, SAVE, APPLY or RATING",
            description = "RATING requires rating 1-5; rating the same treatment again replaces the previous rating. "
                    + "The treatment's counters and average rating are updated automatically.")
    public Interaction record(@Valid @RequestBody InteractionRequest r) {
        return interactions.record(CurrentUser.id(), r);
    }
}
