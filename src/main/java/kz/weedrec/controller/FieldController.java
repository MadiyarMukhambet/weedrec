package kz.weedrec.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kz.weedrec.dto.Dto.FieldRequest;
import kz.weedrec.exception.ApiException;
import kz.weedrec.model.Detection;
import kz.weedrec.model.Field;
import kz.weedrec.repository.DetectionRepository;
import kz.weedrec.repository.FieldRepository;
import kz.weedrec.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/fields")
@RequiredArgsConstructor
@Tag(name = "5. Fields")
public class FieldController {

    private final FieldRepository fields;
    private final DetectionRepository detections;

    @GetMapping
    @Operation(summary = "My fields")
    public List<Field> myFields() {
        return fields.findByUserId(CurrentUser.id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a field")
    public Field create(@Valid @RequestBody FieldRequest r) {
        GeoJsonPoint loc = (r.longitude() != null && r.latitude() != null)
                ? new GeoJsonPoint(r.longitude(), r.latitude()) : null;
        return fields.save(Field.builder()
                .userId(CurrentUser.id())
                .name(r.name())
                .crop(r.crop().trim().toLowerCase())
                .areaHa(r.areaHa())
                .soilType(r.soilType())
                .location(loc)
                .build());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete my field")
    public void delete(@PathVariable String id) {
        fields.delete(owned(id));
    }

    @GetMapping("/{id}/detections")
    @Operation(summary = "Latest weed detections on a field (newest first)")
    public List<Detection> detections(@PathVariable String id, @RequestParam(defaultValue = "10") int limit) {
        owned(id);
        return detections.findByFieldIdOrderByDetectedAtDesc(id, PageRequest.of(0, Math.min(limit, 100)));
    }

    private Field owned(String id) {
        Field f = fields.findById(id).orElseThrow(() -> ApiException.notFound("Field", id));
        if (!f.getUserId().equals(CurrentUser.id())) throw ApiException.notFound("Field", id);
        return f;
    }
}
