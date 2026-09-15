package com.RubiumLead.Service;

import com.RubiumLead.Entity.Lead;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    /*
     * Main discovery method.
     *
     * Existing Overpass leads are preserved.
     * Tavily adds additional businesses.
     */
    public List<Lead> discoverBusinesses(
            List<Lead> existingLeads,
            String city,
            String industry) {

        List<Lead> leads =
                new ArrayList<>();

        if (existingLeads != null) {
            leads.addAll(existingLeads);
        }

        if (tavilyApiKey == null ||
                tavilyApiKey.isBlank() ||
                tavilyApiKey.startsWith("YOUR_")) {

            System.out.println(
                    "⚠️ TAVILY_API_KEY is not configured."
            );

            return leads;
        }

        try {

            List<Lead> tavilyLeads =
                    searchBusinesses(
                            city,
                            industry
                    );

            leads.addAll(tavilyLeads);

            return removeDuplicates(leads);

        } catch (Exception e) {

            System.out.println(
                    "⚠️ Tavily discovery error: " +
                            e.getMessage()
            );

            return removeDuplicates(leads);
        }
    }

    private List<Lead> searchBusinesses(
            String city,
            String industry) {

        List<Lead> leads =
                new ArrayList<>();

        /*
         * Search several variations.
         * This gives better coverage.
         */
        String[] queries = {

                industry +
                        " in " +
                        city,

                "best " +
                        industry +
                        " in " +
                        city,

                industry +
                        " " +
                        city +
                        " official website"
        };

        for (String query : queries) {

            try {

                List<Lead> results =
                        searchTavily(
                                query,
                                city,
                                industry
                        );

                leads.addAll(results);

            } catch (Exception e) {

                System.out.println(
                        "⚠️ Search failed for: " +
                                query
                );
            }
        }

        return removeDuplicates(leads);
    }

    private List<Lead> searchTavily(
            String query,
            String city,
            String industry)
            throws Exception {

        List<Lead> leads =
                new ArrayList<>();

        String requestBody =
                """
                {
                  "query": "%s",
                  "max_results": 10,
                  "search_depth": "basic",
                  "include_answer": false
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

            throw new RuntimeException(
                    "Tavily HTTP " +
                            response.getStatusCode()
                                    .value()
            );
        }

        String body =
                response.getBody();

        if (body == null ||
                body.isBlank()) {

            return leads;
        }

        JsonNode root =
                mapper.readTree(body);

        JsonNode results =
                root.path("results");

        if (!results.isArray()) {
            return leads;
        }

        for (JsonNode result : results) {

            String title =
                    result.path("title")
                            .asText("");

            String url =
                    result.path("url")
                            .asText("");

            String content =
                    result.path("content")
                            .asText("");

            if (!isUsefulResult(
                    title,
                    url,
                    content
            )) {
                continue;
            }

            Lead lead =
                    createLeadFromResult(
                            title,
                            url,
                            content,
                            city,
                            industry
                    );

            if (lead != null) {
                leads.add(lead);
            }
        }

        return leads;
    }

    private Lead createLeadFromResult(
            String title,
            String url,
            String content,
            String city,
            String industry) {

        String business =
                cleanBusinessName(title);

        if (business == null ||
                business.isBlank()) {

            return null;
        }

        Lead lead =
                new Lead();

        lead.setBusiness(business);
        lead.setCategory(
                industry.trim().toLowerCase()
        );
        lead.setLocation(city);
        lead.setWebsite(url);
        lead.setSource("Tavily");

        /*
         * Try to extract phone/email
         * from Tavily content.
         */
        String email =
                extractEmail(content);

        if (email != null) {
            lead.setEmail(email);
        }

        String phone =
                extractPhone(content);

        if (phone != null) {
            lead.setPhone(phone);
        }

        return lead;
    }

    private boolean isUsefulResult(
            String title,
            String url,
            String content) {

        if (url == null ||
                url.isBlank()) {

            return false;
        }

        String value =
                url.toLowerCase();

        /*
         * Do not create leads from
         * directories/social/news sites.
         */
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

    private String cleanBusinessName(
            String title) {

        if (title == null ||
                title.isBlank()) {

            return null;
        }

        String result =
                title.trim();

        /*
         * Remove common search-result
         * suffixes.
         */
        String[] separators = {
                " | ",
                " - ",
                " – ",
                " — "
        };

        for (String separator :
                separators) {

            if (result.contains(separator)) {

                result =
                        result.split(
                                java.util.regex.Pattern
                                        .quote(separator)
                        )[0];

                break;
            }
        }

        return result.trim();
    }

    private String extractEmail(
            String text) {

        if (text == null) {
            return null;
        }

        java.util.regex.Pattern pattern =
                java.util.regex.Pattern.compile(
                        "[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}",
                        java.util.regex.Pattern.CASE_INSENSITIVE
                );

        java.util.regex.Matcher matcher =
                pattern.matcher(text);

        if (matcher.find()) {
            return matcher.group();
        }

        return null;
    }

    private String extractPhone(
            String text) {

        if (text == null) {
            return null;
        }

        java.util.regex.Pattern pattern =
                java.util.regex.Pattern.compile(
                        "(?:\\+91[\\s-]?)?[6-9]\\d{9}"
                );

        java.util.regex.Matcher matcher =
                pattern.matcher(text);

        if (matcher.find()) {
            return matcher.group();
        }

        return null;
    }

    private List<Lead> removeDuplicates(
            List<Lead> leads) {

        List<Lead> result =
                new ArrayList<>();

        Set<String> seen =
                new HashSet<>();

        for (Lead lead : leads) {

            if (lead == null ||
                    lead.getBusiness() == null) {
                continue;
            }

            String key =
                    lead.getBusiness()
                            .trim()
                            .toLowerCase() +
                            "|" +
                            (lead.getLocation() == null
                                    ? ""
                                    : lead.getLocation()
                                    .trim()
                                    .toLowerCase());

            if (seen.add(key)) {
                result.add(lead);
            }
        }

        return result;
    }

    private String escapeJson(
            String value) {

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}