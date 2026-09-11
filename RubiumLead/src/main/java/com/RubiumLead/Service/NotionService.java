package com.RubiumLead.Service;

import com.RubiumLead.Entity.Lead;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.*;
import org.springframework.http.*;
import java.util.*;
import org.springframework.http.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class NotionService {

    private final RestTemplate restTemplate;

    @Value("${notion.token:}")
    private String token;

    @Value("${notion.data-source-id:}")
    private String dataSourceId;

    public NotionService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    // =====================================================
    // MAIN SYNC
    // =====================================================

    public SyncResult syncToNotion(List<Lead> leads) {

        if (token == null || token.isBlank()) {
            System.err.println("❌ Notion token is missing");
            return new SyncResult(0, leads.size());
        }

        if (dataSourceId == null || dataSourceId.isBlank()) {
            System.err.println("❌ Notion Data Source ID is missing");
            return new SyncResult(0, leads.size());
        }

        int synced = 0;
        int failed = 0;

        for (Lead lead : leads) {

            try {
                createPage(lead);

                synced++;

                System.out.println(
                        "✅ Notion synced: " + lead.getBusiness()
                );

            } catch (Exception e) {

                failed++;

                System.err.println(
                        "❌ Notion sync failed: "
                                + lead.getBusiness()
                );

                System.err.println(
                        "Reason: " + e.getMessage()
                );
            }
        }

        System.out.println("================================");
        System.out.println("Notion Sync Complete");
        System.out.println("Synced: " + synced);
        System.out.println("Failed: " + failed);
        System.out.println("================================");

        return new SyncResult(synced, failed);
    }


    // =====================================================
    // CREATE NOTION PAGE
    // =====================================================

    private void createPage(Lead lead) {

        HttpHeaders headers = new HttpHeaders();

        headers.setBearerAuth(token);

        headers.setContentType(
                MediaType.APPLICATION_JSON
        );

        headers.set(
                "Notion-Version",
                "2022-06-28"
        );


        // =================================================
        // PAGE BODY
        // =================================================

        Map<String, Object> body =
                new HashMap<>();

        Map<String, Object> parent =
                new HashMap<>();

        parent.put(
                "data_source_id",
                dataSourceId
        );

        body.put(
                "parent",
                parent
        );


        // =================================================
        // PROPERTIES
        // =================================================

        Map<String, Object> properties =
                new HashMap<>();


        // -------------------------------------------------
        // BUSINESS
        // -------------------------------------------------

        properties.put(
                "Business",
                title(lead.getBusiness())
        );


        // -------------------------------------------------
        // CATEGORY
        // -------------------------------------------------

        if (lead.getCategory() != null
                && !lead.getCategory().isBlank()) {

            properties.put(
                    "Category",
                    richText(lead.getCategory())
            );
        }


        // -------------------------------------------------
        // LOCATION
        // -------------------------------------------------

        if (lead.getLocation() != null
                && !lead.getLocation().isBlank()) {

            properties.put(
                    "Location",
                    richText(lead.getLocation())
            );
        }


        // -------------------------------------------------
        // WEBSITE
        // -------------------------------------------------

        if (lead.getWebsite() != null
                && !lead.getWebsite().isBlank()) {

            properties.put(
                    "Website",
                    url(lead.getWebsite())
            );
        }


        // -------------------------------------------------
        // EMAIL
        // -------------------------------------------------

        if (lead.getEmail() != null
                && !lead.getEmail().isBlank()) {

            properties.put(
                    "Email",
                    email(lead.getEmail())
            );
        }


        // -------------------------------------------------
        // INSTAGRAM
        // -------------------------------------------------

        if (lead.getInstagram() != null
                && !lead.getInstagram().isBlank()) {

            properties.put(
                    "Instagram",
                    url(lead.getInstagram())
            );
        }


        // -------------------------------------------------
        // PHONE
        // -------------------------------------------------

        if (lead.getPhone() != null
                && !lead.getPhone().isBlank()) {

            properties.put(
                    "Phone",
                    phone(lead.getPhone())
            );
        }


        // -------------------------------------------------
        // SOURCE
        // -------------------------------------------------

        if (lead.getSource() != null
                && !lead.getSource().isBlank()) {

            properties.put(
                    "Source",
                    richText(lead.getSource())
            );
        }


        // =================================================
        // SCORES
        // =================================================

        properties.put(
                "HF Fit Score",
                number(lead.getHfFitScore())
        );

        properties.put(
                "Website Score",
                number(lead.getWebsiteScore())
        );

        properties.put(
                "Brand Score",
                number(lead.getBrandScore())
        );


        // =================================================
        // PRIORITY
        // =================================================

        String priority = "C — Skip";

        if (lead.getPriority() != null) {

            priority =
                    switch (lead.getPriority()) {

                        case A_PITCH ->
                                "A — Pitch";

                        case B_REVIEW ->
                                "B — Review";

                        case C_SKIP ->
                                "C — Skip";
                    };
        }

        properties.put(
                "Priority",
                select(priority)
        );


        // =================================================
        // RECOMMENDED OFFER
        // =================================================

        String offer = "Skip";

        if (lead.getRecommendedOffer() != null) {

            offer =
                    switch (
                            lead.getRecommendedOffer()
                            ) {

                        case WEBSITE_REFRESH ->
                                "Website Refresh";

                        case BUSINESS_WEBSITE ->
                                "Business Website";

                        case BRAND_WEBSITE ->
                                "Brand + Website";

                        case SKIP ->
                                "Skip";
                    };
        }

        properties.put(
                "Recommended Offer",
                select(offer)
        );


        // =================================================
        // PROBLEM FOUND
        // =================================================

        if (lead.getProblemFound() != null
                && !lead.getProblemFound().isBlank()) {

            properties.put(
                    "Problem Found",
                    richText(lead.getProblemFound())
            );
        }


        // =================================================
        // PITCH ANGLE
        // =================================================

        if (lead.getPitchAngle() != null
                && !lead.getPitchAngle().isBlank()) {

            properties.put(
                    "Pitch Angle",
                    richText(lead.getPitchAngle())
            );
        }


        // =================================================
        // LAST CHECKED
        // =================================================

        if (lead.getLastChecked() != null
                && !lead.getLastChecked().isBlank()) {

            properties.put(
                    "Last Checked",
                    richText(lead.getLastChecked())
            );
        }


        // =================================================
        // FINAL BODY
        // =================================================

        body.put(
                "properties",
                properties
        );


        // =================================================
        // SEND REQUEST
        // =================================================

        HttpEntity<Map<String, Object>> request =
                new HttpEntity<>(
                        body,
                        headers
                );

        ResponseEntity<String> response =
                restTemplate.postForEntity(
                        "https://api.notion.com/v1/pages",
                        request,
                        String.class
                );


        // =================================================
        // CHECK RESPONSE
        // =================================================

        if (!response.getStatusCode()
                .is2xxSuccessful()) {

            throw new RuntimeException(
                    "Notion API returned "
                            + response.getStatusCode()
                            + " : "
                            + response.getBody()
            );
        }
    }


    // =====================================================
    // NOTION PROPERTY HELPERS
    // =====================================================

    private Map<String, Object> title(String value) {

        Map<String, Object> text =
                new HashMap<>();

        text.put(
                "content",
                value == null ? "" : value
        );

        Map<String, Object> textObject =
                new HashMap<>();

        textObject.put(
                "text",
                text
        );

        Map<String, Object> item =
                new HashMap<>();

        item.put(
                "title",
                List.of(textObject)
        );

        return item;
    }


    private Map<String, Object> richText(
            String value) {

        Map<String, Object> text =
                new HashMap<>();

        text.put(
                "content",
                value == null ? "" : value
        );

        Map<String, Object> textObject =
                new HashMap<>();

        textObject.put(
                "text",
                text
        );

        Map<String, Object> item =
                new HashMap<>();

        item.put(
                "rich_text",
                List.of(textObject)
        );

        return item;
    }


    private Map<String, Object> select(
            String value) {

        Map<String, Object> select =
                new HashMap<>();

        select.put(
                "name",
                value == null
                        ? "Unknown"
                        : value
        );

        Map<String, Object> result =
                new HashMap<>();

        result.put(
                "select",
                select
        );

        return result;
    }


    private Map<String, Object> url(
            String value) {

        Map<String, Object> result =
                new HashMap<>();

        result.put(
                "url",
                value == null || value.isBlank()
                        ? null
                        : value
        );

        return result;
    }


    private Map<String, Object> email(
            String value) {

        Map<String, Object> result =
                new HashMap<>();

        result.put(
                "email",
                value == null || value.isBlank()
                        ? null
                        : value
        );

        return result;
    }


    private Map<String, Object> phone(
            String value) {

        Map<String, Object> result =
                new HashMap<>();

        result.put(
                "phone_number",
                value == null || value.isBlank()
                        ? null
                        : value
        );

        return result;
    }


    private Map<String, Object> number(
            double value) {

        Map<String, Object> result =
                new HashMap<>();

        result.put(
                "number",
                value
        );

        return result;
    }


    // =====================================================
    // RESULT
    // =====================================================

    public record SyncResult(
            int synced,
            int failed
    ) {
    }
}
