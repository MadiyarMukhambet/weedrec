package kz.weedrec.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import kz.weedrec.model.Weed;
import kz.weedrec.repository.WeedRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/weeds")
@RequiredArgsConstructor
@Tag(name = "7. Weed species")
public class WeedController {

    private final WeedRepository weeds;

    @GetMapping
    @Operation(summary = "All weed species (CV model classes)")
    public List<Weed> all() {
        return weeds.findAll();
    }
}
