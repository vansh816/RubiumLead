package com.RubiumLead.Service;

import com.RubiumLead.Entity.Lead;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
public class NotionService {

    private final RestTemplate restTemplate;

    @Value("${notion.token:}")
    private String notionToken;

    @Value("${notion.data-source-id:}")
    private String dataSourceId;

    public NotionService(
            RestTemplate restTemplate) {

        this.restTemplate = restTemplate;
    }

    public void saveLeads(
            List<Lead> leads) {

        if (leads == null ||
                leads.isEmpty()) {

            return;
        }

        if (notionToken == null ||
                notionToken.isBlank() ||
                dataSourceId == null ||
                dataSourceId.isBlank()) {

            System.out.println(
                    "ℹ️ Notion not configured. " +
                            "Skipping Notion sync."
            );

            return;
        }

        System.out.println(
                "ℹ️ Notion sync is configured for " +
                        leads.size() +
                        " leads."
        );

        /*
         * Keep Notion sync isolated here.
         * Discovery and API response do not depend
         * on Notion availability.
         */
    }
}