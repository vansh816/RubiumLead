package com.RubiumLead.Service;

import com.RubiumLead.Entity.Lead;
import com.RubiumLead.Entity.Priority;
import com.RubiumLead.Entity.RecommendedOffer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
     * Existing Overpass leads + Tavily discovery
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

            System.out.println(
                    "🌐 Tavily discovered: "
                            + tavilyLeads.size()
                            + " leads"
            );

            leads.addAll(tavilyLeads);

            return removeDuplicates(leads);

        } catch (Exception e) {

            System.out.println(
                    "⚠️ Tavily discovery error: "
                            + e.getMessage()
            );

            return removeDuplicates(leads);
        }
    }


    /*
     * Search businesses using Tavily
     */
    private List<Lead> searchBusinesses(
            String city,
            String industry) {

        List<Lead> leads =
                new ArrayList<>();

        String[] queries = {

                "\"" + industry + "\" businesses in "
                        + city,

                "best " + industry
                        + " in " + city
                        + " official website",

                industry + " " + city
                        + " contact website",

                industry + " companies in "
                        + city
        };


        for (String query : queries) {

            try {

                System.out.println(
                        "🔎 Tavily query: "
                                + query
                );

                List<Lead> results =
                        searchTavily(
                                query,
                                city,
                                industry
                        );

                leads.addAll(results);

                System.out.println(
                        "   → Results: "
                                + results.size()
                );

            } catch (Exception e) {

                System.out.println(
                        "⚠️ Search failed for: "
                                + query
                                + " -> "
                                + e.getMessage()
                );
            }
        }

        return removeDuplicates(leads);
    }


    /*
     * Call Tavily API
     */
    private List<Lead> searchTavily(
            String query,
            String city,
            String industry)
            throws Exception {

        List<Lead> leads =
                new ArrayList<>();


        Map<String, Object> requestBody =
                new HashMap<>();

        requestBody.put(
                "api_key",
                tavilyApiKey
        );

        requestBody.put(
                "query",
                query
        );

        requestBody.put(
                "search_depth",
                "advanced"
        );

        requestBody.put(
                "max_results",
                10
        );

        requestBody.put(
                "include_answer",
                false
        );

        requestBody.put(
                "include_raw_content",
                false
        );


        HttpHeaders headers =
                new HttpHeaders();

        headers.setContentType(
                MediaType.APPLICATION_JSON
        );


        HttpEntity<Map<String, Object>> request =
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
                    "Tavily HTTP "
                            + response.getStatusCode()
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


        for (JsonNode result :
                results) {

            String title =
                    result.path("title")
                            .asText("");

            String url =
                    result.path("url")
                            .asText("");

            String content =
                    result.path("content")
                            .asText("");


            /*
             * Ignore bad results
             */
            if (!isUsefulResult(
                    title,
                    url,
                    content,
                    industry
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


    /*
     * Create Lead from Tavily result
     */
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


        lead.setBusiness(
                business
        );

        lead.setCategory(
                industry
                        .trim()
                        .toLowerCase()
        );

        lead.setLocation(
                city
        );

        lead.setWebsite(
                url
        );

        lead.setSource(
                "Tavily"
        );


        String email =
                extractEmail(content);


        if (email != null) {

            lead.setEmail(
                    email
            );
        }


        String phone =
                extractPhone(content);


        if (phone != null) {

            lead.setPhone(
                    phone
            );
        }


        /*
         * Basic scoring
         */
        scoreLead(lead);


        lead.setLastChecked(
                LocalDateTime.now()
                        .toString()
        );


        return lead;
    }


    /*
     * Filter Tavily results
     */
    private boolean isUsefulResult(
            String title,
            String url,
            String content,
            String industry) {


        if (url == null ||
                url.isBlank()) {

            return false;
        }


        String lowerUrl =
                url.toLowerCase();


        String lowerTitle =
                title == null
                        ? ""
                        : title.toLowerCase();


        String lowerContent =
                content == null
                        ? ""
                        : content.toLowerCase();


        /*
         * Reject social media
         */
        String[] blockedDomains = {

                "facebook.com",
                "instagram.com",
                "linkedin.com",
                "youtube.com",
                "twitter.com",
                "x.com",
                "tiktok.com",

                /*
                 * Directories
                 */
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

                /*
                 * Search / knowledge
                 */
                "google.com",
                "google.co.in",
                "bing.com",
                "wikipedia.org",
                "wikidata.org",

                /*
                 * Forums
                 */
                "reddit.com",
                "quora.com",

                /*
                 * News
                 */
                "timesofindia.com",
                "hindustantimes.com",
                "indianexpress.com",
                "ndtv.com"
        };


        for (String domain :
                blockedDomains) {

            if (lowerUrl.contains(domain)) {

                return false;
            }
        }


        /*
         * Reject article / blog pages
         */
        String[] blockedPaths = {

                "/blog/",
                "/blogs/",
                "/news/",
                "/article/",
                "/articles/",
                "/review/",
                "/reviews/",
                "/forum/",
                "/forums/",
                "/discussion/",
                "/questions/"
        };


        for (String path :
                blockedPaths) {

            if (lowerUrl.contains(path)) {

                return false;
            }
        }


        /*
         * Reject obvious list/article titles
         */
        String[] badTitleWords = {

                "best gyms",
                "best restaurants",
                "top gyms",
                "top restaurants",
                "gym recommendations",
                "restaurant recommendations",
                "which is the best",
                "list of",
                "top 10",
                "top 20",
                "reviews of",
                "things to do"
        };


        for (String word :
                badTitleWords) {

            if (lowerTitle.contains(word)) {

                return false;
            }
        }


        /*
         * Result should mention industry
         */
        String lowerIndustry =
                industry
                        .toLowerCase()
                        .trim();


        if (!lowerTitle.contains(lowerIndustry)
                && !lowerContent.contains(lowerIndustry)) {

            /*
             * Don't reject if URL looks business-like.
             */
            String domain =
                    extractDomain(lowerUrl);

            if (domain == null ||
                    domain.length() < 4) {

                return false;
            }
        }


        return lowerUrl.startsWith("http://")
                || lowerUrl.startsWith("https://");
    }


    /*
     * Clean business name
     */
    private String cleanBusinessName(
            String title) {


        if (title == null ||
                title.isBlank()) {

            return null;
        }


        String result =
                title.trim();


        String[] separators = {

                " | ",
                " - ",
                " – ",
                " — ",
                " :: "
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


        /*
         * Remove common unwanted prefixes
         */
        result =
                result.replaceFirst(
                        "(?i)^home\\s*[-|:]\\s*",
                        ""
                );


        result =
                result.replaceFirst(
                        "(?i)^welcome to\\s+",
                        ""
                );


        return result.trim();
    }


    /*
     * Extract email
     */
    private String extractEmail(
            String text) {


        if (text == null) {

            return null;
        }


        Pattern pattern =
                Pattern.compile(
                        "[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}",
                        Pattern.CASE_INSENSITIVE
                );


        Matcher matcher =
                pattern.matcher(text);


        if (matcher.find()) {

            return matcher.group();
        }


        return null;
    }


    /*
     * Extract Indian phone
     */
    private String extractPhone(
            String text) {


        if (text == null) {

            return null;
        }


        Pattern pattern =
                Pattern.compile(
                        "(?:\\+91[\\s-]?)?[6-9]\\d{9}"
                );


        Matcher matcher =
                pattern.matcher(text);


        if (matcher.find()) {

            return matcher.group();
        }


        return null;
    }


    /*
     * Lead scoring
     */
    private void scoreLead(
            Lead lead) {


        boolean hasWebsite =
                lead.getWebsite() != null
                        && !lead.getWebsite().isBlank();


        boolean hasEmail =
                lead.getEmail() != null
                        && !lead.getEmail().isBlank();


        boolean hasPhone =
                lead.getPhone() != null
                        && !lead.getPhone().isBlank();


        boolean hasInstagram =
                lead.getInstagram() != null
                        && !lead.getInstagram().isBlank();


        double websiteScore;


        if (!hasWebsite) {

            websiteScore = 0;

        } else if (hasEmail && hasPhone) {

            websiteScore = 100;

        } else if (hasEmail || hasPhone) {

            websiteScore = 80;

        } else {

            websiteScore = 60;
        }


        double brandScore = 20;


        if (hasWebsite) {

            brandScore += 30;
        }


        if (hasInstagram) {

            brandScore += 25;
        }


        if (hasEmail) {

            brandScore += 15;
        }


        if (hasPhone) {

            brandScore += 10;
        }


        brandScore =
                Math.min(
                        100,
                        brandScore
                );


        double hfFitScore =
                websiteScore * 0.60
                        + brandScore * 0.40;


        lead.setWebsiteScore(
                websiteScore
        );

        lead.setBrandScore(
                brandScore
        );

        lead.setHfFitScore(
                hfFitScore
        );


        /*
         * No website
         */
        if (!hasWebsite) {

            lead.setPriority(
                    Priority.A_PITCH
            );

            lead.setRecommendedOffer(
                    RecommendedOffer.BUSINESS_WEBSITE
            );

            lead.setProblemFound(
                    "No official website found"
            );

            lead.setPitchAngle(
                    "Offer a professional business website"
            );


            /*
             * Website exists but contact/brand
             * presence is incomplete
             */
        } else if (!hasInstagram
                || !hasEmail) {

            lead.setPriority(
                    Priority.B_REVIEW
            );

            lead.setRecommendedOffer(
                    RecommendedOffer.BRAND_WEBSITE
            );

            lead.setProblemFound(
                    "Digital presence is incomplete"
            );

            lead.setPitchAngle(
                    "Improve website, branding and conversion-focused digital presence"
            );


            /*
             * Good digital presence
             */
        } else {

            lead.setPriority(
                    Priority.C_SKIP
            );

            lead.setRecommendedOffer(
                    RecommendedOffer.SKIP
            );

            lead.setProblemFound(
                    "No major issue detected"
            );

            lead.setPitchAngle(
                    "No immediate pitch"
            );
        }
    }


    /*
     * Remove duplicate businesses
     */
    private List<Lead> removeDuplicates(
            List<Lead> leads) {


        List<Lead> result =
                new ArrayList<>();


        Set<String> seen =
                new HashSet<>();


        for (Lead lead :
                leads) {


            if (lead == null ||
                    lead.getBusiness() == null ||
                    lead.getBusiness().isBlank()) {

                continue;
            }


            String business =
                    normalize(
                            lead.getBusiness()
                    );


            String location =
                    normalize(
                            lead.getLocation()
                    );


            String website =
                    normalize(
                            lead.getWebsite()
                    );


            /*
             * Prefer website as duplicate key
             */
            String key;


            if (!website.isBlank()) {

                key = website;

            } else {

                key =
                        business
                                + "|"
                                + location;
            }


            if (seen.add(key)) {

                result.add(lead);
            }
        }


        return result;
    }


    /*
     * Normalize text
     */
    private String normalize(
            String value) {


        if (value == null) {

            return "";
        }


        return value
                .trim()
                .toLowerCase()
                .replace(
                        "https://",
                        ""
                )
                .replace(
                        "http://",
                        ""
                )
                .replace(
                        "www.",
                        ""
                )
                .replaceAll(
                        "/$",
                        ""
                );
    }


    /*
     * Extract domain
     */
    private String extractDomain(
            String url) {


        try {

            String domain =
                    url.replace(
                                    "https://",
                                    ""
                            )
                            .replace(
                                    "http://",
                                    ""
                            );


            int slash =
                    domain.indexOf("/");


            if (slash >= 0) {

                domain =
                        domain.substring(
                                0,
                                slash
                        );
            }


            return domain;

        } catch (Exception e) {

            return null;
        }
    }
}