package com.RubiumLead.Controller;

import com.RubiumLead.Entity.Lead;
import com.RubiumLead.Service.LeadEngineService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/leads")
@CrossOrigin(origins = "*")
public class LeadController {

    private final LeadEngineService leadEngineService;

    public LeadController(
            LeadEngineService leadEngineService) {

        this.leadEngineService =
                leadEngineService;
    }

    @GetMapping("/run")
    public ResponseEntity<List<Lead>> run(
            @RequestParam String city,
            @RequestParam String industry) {

        System.out.println(
                "🔥 REQUEST RECEIVED"
        );

        System.out.println(
                "🏙️ City = " + city
        );

        System.out.println(
                "🏢 Industry = " + industry
        );

        try {

            List<Lead> leads =
                    leadEngineService.run(
                            city,
                            industry
                    );

            return ResponseEntity.ok(leads);

        } catch (Exception e) {

            System.out.println(
                    "❌ Lead engine error: " +
                            e.getMessage()
            );

            e.printStackTrace();

            return ResponseEntity
                    .status(500)
                    .body(List.of());
        }
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {

        return ResponseEntity.ok(
                "Rubium Lead Engine is running"
        );
    }
}