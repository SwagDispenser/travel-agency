package com.example.travelagency.tour;

import com.example.travelagency.web.annotation.CreatedPost;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tours")
public class TourRestController {

    private final TourService tourService;

    public TourRestController(TourService tourService) {
        this.tourService = tourService;
    }

    @GetMapping
    public List<TourResponse> findAll() {
        return tourService.findAll();
    }

    @GetMapping("/{id}")
    public TourResponse findById(@PathVariable Long id) {
        return tourService.findById(id);
    }

    @GetMapping("/{id}/quote")
    public TourQuote quote(@PathVariable Long id, @RequestParam(defaultValue = "1") int passengers) {
        return tourService.quote(id, passengers);
    }

    @CreatedPost
    public TourResponse create(@Valid @RequestBody TourRequest request) {
        return tourService.create(request);
    }

    @PutMapping("/{id}")
    public TourResponse update(@PathVariable Long id, @Valid @RequestBody TourRequest request) {
        return tourService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        tourService.delete(id);
    }

    @PostMapping("/{id}/toggle-featured")
    public TourResponse toggleFeatured(@PathVariable Long id) {
        return tourService.toggleFeatured(id);
    }
}
