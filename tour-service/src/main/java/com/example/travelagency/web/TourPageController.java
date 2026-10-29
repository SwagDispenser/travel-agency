package com.example.travelagency.web;

import com.example.travelagency.tour.TourRequest;
import com.example.travelagency.tour.TourResponse;
import com.example.travelagency.tour.TourService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;


@Controller
@RequestMapping("/tours")
public class TourPageController {
    private final TourService tourService;

    public TourPageController(TourService tourService) {
        this.tourService = tourService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("tours", tourService.findAll());
        return "tours/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("tour", new TourForm());
        model.addAttribute("mode", "create");
        return "tours/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("tour") TourForm form,
                         BindingResult bindingResult,
                         Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("mode", "create");
            return "tours/form";
        }
        tourService.create(toRequest(form));
        return "redirect:/tours";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("tour", toForm(tourService.findById(id)));
        model.addAttribute("mode", "edit");
        model.addAttribute("tourId", id);
        return "tours/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("tour") TourForm form,
                         BindingResult bindingResult,
                         Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("mode", "edit");
            model.addAttribute("tourId", id);
            return "tours/form";
        }
        tourService.update(id, toRequest(form));
        return "redirect:/tours";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        tourService.delete(id);
        return "redirect:/tours";
    }

    @PostMapping("/{id}/toggle-featured")
    public String toggleFeatured(@PathVariable Long id) {
        tourService.toggleFeatured(id);
        return "redirect:/tours";
    }

    private TourRequest toRequest(TourForm form) {
        return new TourRequest(
                form.getTitle(),
                form.getCountry(),
                form.getCity(),
                form.getDescription(),
                form.getStartDate(),
                form.getEndDate(),
                form.getPrice(),
                form.getAvailableSeats(),
                form.isFeatured()
        );
    }

    private TourForm toForm(TourResponse response) {
        TourForm form = new TourForm();
        form.setTitle(response.title());
        form.setCountry(response.country());
        form.setCity(response.city());
        form.setDescription(response.description());
        form.setStartDate(response.startDate());
        form.setEndDate(response.endDate());
        form.setPrice(response.price());
        form.setAvailableSeats(response.availableSeats());
        form.setFeatured(response.featured());
        return form;
    }
}
