package com.example.travelagency.web;

import com.example.travelagency.web.annotation.CurrentUserId;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/travelers")
public class TravelerController {
    @GetMapping("/me")
    public Map<String, String> currentTraveler(@CurrentUserId String userId) {
        return Map.of("userId", userId);
    }
}
