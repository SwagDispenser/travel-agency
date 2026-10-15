package com.example.travelagency.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class AdminController {

    @GetMapping("/admin/status")
    public Map<String, String> status() {
        return Map.of("status", "Only ADMIN can read this endpoint");
    }
}
