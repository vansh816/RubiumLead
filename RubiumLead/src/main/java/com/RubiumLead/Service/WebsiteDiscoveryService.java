package com.RubiumLead.Service;

import com.RubiumLead.Entity.Lead;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
public class WebsiteDiscoveryService {

    private final RestTemplate restTemplate;
    private final ObjectMapper mapper;

    @Value("${tavily.api-key:}")
    private String tavilyApiKey;

    public WebsiteDiscoveryService(
            RestTemplate restTemplate,
            ObjectMapper mapper) {

        this.restTemplate = restTemplate;
        this.mapper = mapper;
    }

    public List<Lead> discoverWebsites(
            List<Lead> leads,
            String city) {

        if (leads == null || leads.isEmpty()) {
            return leads;
        }

        if (tavilyApiKey == null ||
                tavilyApiKey.isBlank() ||
                tavilyApiKey.startsWith("YOUR_")) {

            System.out.println(
                    "ℹ️ Tavily not configured. Skipping website discovery."
            );

            return leads;
        }

        for (Lead lead : leads) {

            if (lead.getWebsite() != null &&
                    !lead.getWebsite().isBlank()) {

                continue;
            }

            try {

                String website =
                        searchWebsite(
                                lead.getBusiness(),
                                city
                        );

                if (website != null &&
                        !website.isBlank()) {

                    lead.setWebsite(website);

                    System.out.println(
                            "🌐 Website found for " +
                                    lead.getBusiness() +
                                    " -> " +
                                    website
                    );
                }

            } catch (Exception e) {

                System.out.println(
                        "⚠️ Website search failed for " +
                                lead.getBusiness() +
                                " -> " +
                                e.getMessage()
                );
            }
        }

        return leads;
    }

    private String searchWebsite(
            String business,
            String city) {

        try {

            String query =
                    business +
                            " " +
                            city +
                            " official website";

            String requestBody =
                    """
                    {
                      "query": "%s",
                      "max_results": 5,
                      "search_depth": "basic"
                    }
                    """.formatted(
                            escapeJson(query)
                    );

            HttpHeaders headers =
                    new HttpHeaders();

            headers.setContentType(
                    MediaType.APPLICATION_JSON
            );

            headers.setBearerAuth(
                    tavilyApiKey
            );

            HttpEntity<String> request =
                    new HttpEntity<>(
                            requestBody,
                            headers
                    );

            ResponseEntity<String> response =
                    restTemplate.postForEntity(
                            "https://api.tavily.com/search",
                            request,
                            String.class
                    );

            if (!response.getStatusCode()
                    .is2xxSuccessful()) {

                System.out.println(
                        "⚠️ Tavily HTTP status: " +
                                response.getStatusCode().value()
                );

                return null;
            }

            String responseBody =
                    response.getBody();

            if (responseBody == null ||
                    responseBody.isBlank()) {

                return null;
            }

            // FIX:
            // readTree() is now inside try-catch
            JsonNode root =
                    mapper.readTree(responseBody);

            JsonNode results =
                    root.path("results");

            if (!results.isArray()) {
                return null;
            }

            for (JsonNode result : results) {

                String url =
                        result.path("url")
                                .asText("");

                if (isValidBusinessWebsite(url)) {
                    return url;
                }
            }

            return null;

        } catch (Exception e) {

            System.out.println(
                    "⚠️ Tavily search error: " +
                            e.getMessage()
            );

            return null;
        }
    }

    private boolean isValidBusinessWebsite(
            String url) {

        if (url == null ||
                url.isBlank()) {

            return false;
        }

        String value =
                url.toLowerCase();

        String[] blocked = {

                "facebook.com",
                "instagram.com",
                "linkedin.com",
                "youtube.com",
                "twitter.com",
                "x.com",
                "tiktok.com",

                "zomato.com",
                "swiggy.com",
                "eazydiner.com",
                "justdial.com",
                "tripadvisor.com",
                "yelp.com",
                "magicpin.in",
                "nearbuy.com",
                "sulekha.com",
                "foursquare.com",

                "google.com",
                "google.co.in",
                "bing.com",

                "wikipedia.org",
                "wikidata.org",

                "timesofindia.com",
                "hindustantimes.com",
                "indianexpress.com",
                "ndtv.com"
        };

        for (String domain : blocked) {

            if (value.contains(domain)) {
                return false;
            }
        }

        return value.startsWith("http://") ||
                value.startsWith("https://");
    }

    private String escapeJson(
            String value) {

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}