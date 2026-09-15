package com.RubiumLead.Service;

import com.RubiumLead.Entity.Lead;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class LeadEngineService {

    private final OverpassService overpassService;
    private final WebsiteDiscoveryService websiteDiscoveryService;
    private final EnrichmentService enrichmentService;
    private final NotionService notionService;

    public LeadEngineService(
            OverpassService overpassService,
            WebsiteDiscoveryService websiteDiscoveryService,
            EnrichmentService enrichmentService,
            NotionService notionService) {

        this.overpassService = overpassService;
        this.websiteDiscoveryService = websiteDiscoveryService;
        this.enrichmentService = enrichmentService;
        this.notionService = notionService;
    }

    public List<Lead> run(
            String city,
            String industry) {

        System.out.println("================================");
        System.out.println("🚀 RUBIUM LEAD ENGINE");
        System.out.println("City     : " + city);
        System.out.println("Industry : " + industry);
        System.out.println("================================");

        /*
         * STEP 1
         * Try Overpass first.
         *
         * If Overpass fails, DO NOT stop the application.
         */
        List<Lead> leads = new ArrayList<>();

        try {

            leads.addAll(
                    overpassService.discover(
                            industry,
                            city
                    )
            );

            System.out.println(
                    "🗺️ Overpass leads: " +
                            leads.size()
            );

        } catch (Exception e) {

            System.out.println(
                    "⚠️ Overpass unavailable."
            );

            System.out.println(
                    "⚠️ Reason: " +
                            e.getMessage()
            );
        }

        /*
         * STEP 2
         * Tavily discovery.
         *
         * This works even when Overpass fails.
         */
        try {

            leads =
                    websiteDiscoveryService
                            .discoverBusinesses(
                                    leads,
                                    city,
                                    industry
                            );

            System.out.println(
                    "🌐 After Tavily discovery: " +
                            leads.size()
            );

        } catch (Exception e) {

            System.out.println(
                    "⚠️ Tavily discovery failed: " +
                            e.getMessage()
            );
        }

        /*
         * STEP 3
         * Enrichment + scoring
         */
        if (!leads.isEmpty()) {

            leads =
                    enrichmentService.enrich(
                            leads
                    );
        }

        /*
         * STEP 4
         * IMPORTANT:
         * Make mutable list before sorting.
         */
        List<Lead> sortedLeads =
                new ArrayList<>(leads);

        sortedLeads.sort(
                Comparator
                        .comparingDouble(
                                Lead::getHfFitScore
                        )
                        .reversed()
        );

        /*
         * STEP 5
         * Notion is optional.
         */
        try {

            notionService.saveLeads(
                    sortedLeads
            );

        } catch (Exception e) {

            System.out.println(
                    "⚠️ Notion sync failed: " +
                            e.getMessage()
            );
        }

        System.out.println("================================");
        System.out.println(
                "✅ FINAL LEADS: " +
                        sortedLeads.size()
        );
        System.out.println("================================");

        return sortedLeads;
    }
}