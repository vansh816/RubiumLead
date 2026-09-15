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

        this.overpassService =
                overpassService;

        this.websiteDiscoveryService =
                websiteDiscoveryService;

        this.enrichmentService =
                enrichmentService;

        this.notionService =
                notionService;
    }

    public List<Lead> run(
            String city,
            String industry) {

        System.out.println("================================");
        System.out.println("🚀 RUBIUM LEAD ENGINE");
        System.out.println("City     : " + city);
        System.out.println("Industry : " + industry);
        System.out.println("================================");

        List<Lead> leads =
                overpassService.discover(
                        industry,
                        city
                );

        System.out.println(
                "📦 Discovery result: " +
                        leads.size()
        );

        if (leads.isEmpty()) {

            System.out.println(
                    "⚠️ No leads found."
            );

            return new ArrayList<>();
        }

        leads =
                websiteDiscoveryService
                        .discoverWebsites(
                                leads,
                                city
                        );

        leads =
                enrichmentService
                        .enrich(leads);

        /*
         * IMPORTANT:
         * Always create mutable ArrayList.
         * This avoids ImmutableCollections.uoe.
         */
        List<Lead> sortedLeads =
                new ArrayList<>(leads);

        sortedLeads.sort(
                Comparator.comparingDouble(
                        Lead::getHfFitScore
                ).reversed()
        );

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

        System.out.println(
                "✅ FINAL LEADS: " +
                        sortedLeads.size()
        );

        return sortedLeads;
    }
}