package com.RubiumLead.Service;

import com.RubiumLead.Entity.Lead;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class LeadEngineService {

    private final OverpassService overpassService;
    private final WebsiteDiscoveryService websiteDiscoveryService;
    private final EnrichmentService enrichmentService;
    private final ObjectMapper mapper;


    public LeadEngineService(
            OverpassService overpassService,
            WebsiteDiscoveryService websiteDiscoveryService,
            EnrichmentService enrichmentService,
            ObjectMapper mapper) {

        this.overpassService =
                overpassService;

        this.websiteDiscoveryService =
                websiteDiscoveryService;

        this.enrichmentService =
                enrichmentService;

        this.mapper =
                mapper;
    }


    public List<Lead> run(
            String city,
            String industry) {


        // 1. DISCOVER
        List<Lead> leads =
                overpassService.discover(
                        industry,
                        city
                );


        System.out.println(
                "🔎 Total discovered: "
                        + leads.size()
        );


        // 2. REMOVE DUPLICATES
        leads =
                removeDuplicates(leads);


        System.out.println(
                "🧹 After duplicate removal: "
                        + leads.size()
        );


        // 3. TESTING LIMIT
        // Keep 5 for testing.
        // Remove this block when final.
        leads =
                new ArrayList<>(
                        leads.stream()
                                .limit(5)
                                .toList()
                );


        // 4. WEBSITE DISCOVERY
        for (Lead lead : leads) {

            System.out.println(
                    "🔍 Processing: "
                            + lead.getBusiness()
            );


            websiteDiscoveryService
                    .discover(lead);
        }


        // 5. ENRICH
        leads =
                enrichmentService.enrich(
                        leads
                );


        // 6. SORT BY SCORE
        leads.sort(
                Comparator
                        .comparingDouble(
                                Lead::getHfFitScore
                        )
                        .reversed()
        );


        // 7. SAVE JSON
        saveLeads(leads);


        return leads;
    }


    private List<Lead> removeDuplicates(
            List<Lead> leads) {


        return new ArrayList<>(

                leads.stream()

                        .collect(
                                Collectors.toMap(

                                        lead ->
                                                normalizeName(
                                                        lead.getBusiness()
                                                )
                                                        + "|"
                                                        + normalizeName(
                                                        lead.getLocation()
                                                ),

                                        lead ->
                                                lead,

                                        (
                                                first,
                                                second
                                        ) ->

                                                dataCount(second)
                                                        >
                                                        dataCount(first)
                                                        ? second
                                                        : first
                                )
                        )

                        .values()
        );
    }


    private int dataCount(
            Lead lead) {

        int count = 0;


        if (hasValue(
                lead.getWebsite()
        )) {

            count++;
        }


        if (hasValue(
                lead.getEmail()
        )) {

            count++;
        }


        if (hasValue(
                lead.getPhone()
        )) {

            count++;
        }


        if (hasValue(
                lead.getInstagram()
        )) {

            count++;
        }


        return count;
    }


    private String normalizeName(
            String value) {

        if (value == null) {
            return "";
        }


        return value
                .toLowerCase()
                .replaceAll(
                        "[^a-z0-9]",
                        ""
                );
    }


    private boolean hasValue(
            String value) {

        return value != null
                && !value.isBlank();
    }


    private void saveLeads(
            List<Lead> leads) {

        try {

            Files.createDirectories(
                    Path.of("data")
            );


            mapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValue(
                            Path.of(
                                    "data/leads.json"
                            ).toFile(),
                            leads
                    );


            System.out.println(
                    "💾 Saved "
                            + leads.size()
                            + " leads to data/leads.json"
            );


        } catch (Exception e) {

            throw new RuntimeException(
                    "Failed to save leads",
                    e
            );
        }
    }
}