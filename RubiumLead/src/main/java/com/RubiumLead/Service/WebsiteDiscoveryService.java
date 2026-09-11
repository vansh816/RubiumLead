package com.RubiumLead.Service;

import com.RubiumLead.Entity.Lead;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.List;

@Service
public class WebsiteDiscoveryService {

    private final RestTemplate restTemplate;
    private final ObjectMapper mapper;

    @Value("${tavily.api-key}")
    private String apiKey;


    public WebsiteDiscoveryService(
            RestTemplate restTemplate,
            ObjectMapper mapper) {

        this.restTemplate = restTemplate;
        this.mapper = mapper;
    }


    public void discover(Lead lead) {

        // OSM already has website.
        // NEVER overwrite it.
        if (hasValue(lead.getWebsite())) {

            lead.setWebsite(
                    cleanUrl(
                            lead.getWebsite()
                    )
            );

            return;
        }


        try {

            String business =
                    cleanBusinessName(
                            lead.getBusiness()
                    );

            String city =
                    lead.getLocation();


            String query =
                    "\"" + business + "\" "
                            + "\"" + city + "\" "
                            + "official website";


            String json = """
                    {
                      "api_key": "%s",
                      "query": "%s",
                      "search_depth": "basic",
                      "max_results": 10,
                      "include_answer": false,
                      "include_raw_content": false
                    }
                    """.formatted(
                    escape(apiKey),
                    escape(query)
            );


            HttpHeaders headers =
                    new HttpHeaders();

            headers.setContentType(
                    MediaType.APPLICATION_JSON
            );


            ResponseEntity<String> response =
                    restTemplate.exchange(
                            "https://api.tavily.com/search",
                            HttpMethod.POST,
                            new HttpEntity<>(
                                    json,
                                    headers
                            ),
                            String.class
                    );


            JsonNode results =
                    mapper.readTree(
                            response.getBody()
                    ).path("results");


            if (!results.isArray()) {
                return;
            }


            for (JsonNode result : results) {

                String url =
                        result.path("url")
                                .asText("");

                String title =
                        result.path("title")
                                .asText("");

                String content =
                        result.path("content")
                                .asText("");


                if (!isAllowedDomain(url)) {
                    continue;
                }


                if (!isBusinessRelevant(
                        business,
                        title,
                        content,
                        url
                )) {

                    continue;
                }


                // Additional domain sanity check
                if (!looksLikeOfficialWebsite(
                        business,
                        url
                )) {

                    continue;
                }


                lead.setWebsite(
                        cleanUrl(url)
                );


                lead.setSource(
                        "OpenStreetMap + Tavily"
                );


                System.out.println(
                        "🌐 Verified website: "
                                + business
                                + " -> "
                                + url
                );


                return;
            }


            System.out.println(
                    "⚠️ No reliable website found for "
                            + business
            );


        } catch (Exception e) {

            System.out.println(
                    "⚠️ Website discovery failed for "
                            + lead.getBusiness()
                            + ": "
                            + e.getMessage()
            );
        }
    }


    private boolean isAllowedDomain(
            String url) {

        if (!hasValue(url)) {
            return false;
        }


        try {

            URI uri =
                    URI.create(
                            cleanUrl(url)
                    );


            String host =
                    uri.getHost();


            if (host == null) {
                return false;
            }


            host =
                    host.toLowerCase();


            List<String> blocked =
                    List.of(

                            // SOCIAL
                            "facebook.com",
                            "instagram.com",
                            "linkedin.com",
                            "youtube.com",
                            "twitter.com",
                            "x.com",
                            "tiktok.com",

                            // FOOD / BUSINESS DIRECTORIES
                            "eazydiner.com",
                            "zomato.com",
                            "swiggy.com",
                            "justdial.com",
                            "tripadvisor.com",
                            "yelp.com",
                            "magicpin.in",
                            "nearbuy.com",
                            "sulekha.com",
                            "foursquare.com",

                            // CUSTOMER CARE
                            "indiacustomercare.com",
                            "customercare.com",

                            // COMPLAINTS
                            "pissedconsumer.com",
                            "complaintsboard.com",

                            // KNOWLEDGE
                            "wikipedia.org",
                            "wikidata.org",

                            // DESIGN PORTFOLIOS
                            "behance.net",
                            "dribbble.com",

                            // NEWS
                            "timesofindia.com",
                            "hindustantimes.com",
                            "indianexpress.com",
                            "ndtv.com",

                            // SEARCH
                            "google.com",
                            "google.co.in",
                            "bing.com"
                    );


            for (String domain : blocked) {

                if (host.equals(domain)
                        || host.endsWith(
                        "." + domain
                )) {

                    return false;
                }
            }


            return true;


        } catch (Exception e) {

            return false;
        }
    }


    private boolean isBusinessRelevant(
            String business,
            String title,
            String content,
            String url) {

        String normalizedBusiness =
                normalize(business);


        String text =
                normalize(
                        title
                                + " "
                                + content
                                + " "
                                + url
                );


        String[] words =
                normalizedBusiness
                        .split(" ");


        int meaningfulWords = 0;
        int matchedWords = 0;


        for (String word : words) {

            if (word.length() < 3) {
                continue;
            }


            meaningfulWords++;


            if (text.contains(word)) {
                matchedWords++;
            }
        }


        if (meaningfulWords == 0) {
            return false;
        }


        int required =
                Math.max(
                        1,
                        (int) Math.ceil(
                                meaningfulWords * 0.5
                        )
                );


        return matchedWords >= required;
    }


    private boolean looksLikeOfficialWebsite(
            String business,
            String url) {

        try {

            URI uri =
                    URI.create(
                            cleanUrl(url)
                    );


            String host =
                    uri.getHost();


            if (host == null) {
                return false;
            }


            host =
                    host
                            .toLowerCase()
                            .replace(
                                    "www.",
                                    ""
                            );


            String businessNormalized =
                    normalize(business)
                            .replace(" ", "");


            String hostNormalized =
                    host
                            .replaceAll(
                                    "[^a-z0-9]",
                                    ""
                            );


            /*
             * Strong signal:
             * business name appears in domain.
             *
             * Example:
             * bluetokaicoffee.com
             * cafecoffeeday.com
             */
            if (hostNormalized.contains(
                    businessNormalized
            )) {

                return true;
            }


            /*
             * For short/common names like
             * Roots, Nike etc. domain match
             * isn't mandatory.
             *
             * These can still pass if Tavily
             * content strongly matches.
             */
            String[] words =
                    normalize(business)
                            .split(" ");


            int matches = 0;
            int meaningful = 0;


            for (String word : words) {

                if (word.length() < 4) {
                    continue;
                }


                meaningful++;


                if (hostNormalized.contains(
                        word
                )) {

                    matches++;
                }
            }


            return meaningful > 0
                    && matches >= 1;


        } catch (Exception e) {

            return false;
        }
    }


    private String cleanBusinessName(
            String name) {

        if (name == null) {
            return "";
        }


        return name
                .replaceAll(
                        "(?i)\\b(cafe|cafes)\\b",
                        " "
                )
                .replaceAll(
                        "\\s+",
                        " "
                )
                .trim();
    }


    private String normalize(
            String value) {

        if (value == null) {
            return "";
        }


        return value
                .toLowerCase()
                .replaceAll(
                        "[^a-z0-9]",
                        " "
                )
                .replaceAll(
                        "\\s+",
                        " "
                )
                .trim();
    }


    private String cleanUrl(
            String url) {

        if (url == null) {
            return null;
        }


        return url
                .replace("[", "")
                .replace("]", "")
                .trim();
    }


    private boolean hasValue(
            String value) {

        return value != null
                && !value.isBlank();
    }


    private String escape(
            String value) {

        if (value == null) {
            return "";
        }


        return value
                .replace(
                        "\\",
                        "\\\\"
                )
                .replace(
                        "\"",
                        "\\\""
                );
    }
}