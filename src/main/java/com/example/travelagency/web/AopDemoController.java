package com.example.travelagency.web;

import com.example.travelagency.tour.TourQuote;
import com.example.travelagency.tour.TourService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/aop-demo/tours/{id}")
public class AopDemoController {
    private final TourService tourService;

    public AopDemoController(TourService tourService) {
        this.tourService = tourService;
    }

    @GetMapping("/external")
    public TourQuote external(@PathVariable Long id, @RequestParam(defaultValue = "2") int passengers) {
        return tourService.quote(id, passengers);
    }

    @GetMapping("/self-invocation")
    public TourQuote selfInvocation(@PathVariable Long id, @RequestParam(defaultValue = "2") int passengers) {
        return tourService.quoteViaSelfInvocation(id, passengers);
    }
}
