package com.RubiumLead.Controller;

import com.RubiumLead.Entity.Lead;
import com.RubiumLead.Service.LeadEngineService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/leads")
public class LeadController {

    private final LeadEngineService leadEngineService;


    public LeadController(
            LeadEngineService leadEngineService) {

        this.leadEngineService =
                leadEngineService;
    }


    @GetMapping("/run")
    public List<Lead> run(
            @RequestParam String city,
            @RequestParam String industry) {

        return leadEngineService.run(
                city,
                industry
        );
    }
}