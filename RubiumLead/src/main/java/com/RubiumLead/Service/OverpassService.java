package com.RubiumLead.Service;

import com.RubiumLead.Entity.Lead;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class OverpassService {

    private final RestTemplate restTemplate;
    private final ObjectMapper mapper;

    private final List<String> servers = List.of(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter",
            "https://overpass.private.coffee/api/interpreter"
    );

    public OverpassService(
            RestTemplate restTemplate,
            ObjectMapper mapper) {

        this.restTemplate = restTemplate;
        this.mapper = mapper;
    }

    public List<Lead> discover(
            String industry,
            String city) {

        if (industry == null ||
                industry.isBlank()) {

            throw new IllegalArgumentException(
                    "Industry is required"
            );
        }

        if (city == null ||
                city.isBlank()) {

            throw new IllegalArgumentException(
                    "City is required"
            );
        }

        System.out.println(
                "===================================="
        );

        System.out.println(
                "🔎 OVERPASS DISCOVERY"
        );

        System.out.println(
                "🏙️ City     : " + city
        );

        System.out.println(
                "🏢 Industry : " + industry
        );

        System.out.println(
                "===================================="
        );

        double[] coordinates =
                getCoordinates(city);

        String filter =
                getFilter(industry);

        String query =
                """
                [out:json][timeout:20];
                (
                  nwr(around:8000,%s,%s)%s;
                );
                out center tags;
                """.formatted(
                        coordinates[0],
                        coordinates[1],
                        filter
                );

        Exception lastException = null;

        for (String server : servers) {

            try {

                System.out.println(
                        "🌍 Trying Overpass: " +
                                server
                );

                List<Lead> leads =
                        executeQuery(
                                server,
                                query,
                                city,
                                industry
                        );

                System.out.println(
                        "✅ Overpass returned " +
                                leads.size() +
                                " leads"
                );

                return leads;

            } catch (Exception e) {

                lastException = e;

                System.out.println(
                        "⚠️ Overpass failed: " +
                                server +
                                " -> " +
                                e.getMessage()
                );
            }
        }

        throw new RuntimeException(
                "All Overpass servers failed",
                lastException
        );
    }

    private List<Lead> executeQuery(
            String server,
            String query,
            String city,
            String industry) {

        HttpHeaders headers =
                new HttpHeaders();

        headers.setContentType(
                MediaType.APPLICATION_FORM_URLENCODED
        );

        headers.set(
                "User-Agent",
                "RubiumAI-LeadEngine/1.0"
        );

        String body =
                "data=" +
                        URLEncoder.encode(
                                query,
                                StandardCharsets.UTF_8
                        );

        HttpEntity<String> request =
                new HttpEntity<>(
                        body,
                        headers
                );

        ResponseEntity<String> response =
                restTemplate.postForEntity(
                        server,
                        request,
                        String.class
                );

        if (!response.getStatusCode()
                .is2xxSuccessful()) {

            throw new RuntimeException(
                    "HTTP " +
                            response.getStatusCode()
                                    .value()
            );
        }

        String responseBody =
                response.getBody();

        if (responseBody == null ||
                responseBody.isBlank()) {

            throw new RuntimeException(
                    "Empty response"
            );
        }

        return parseResponse(
                responseBody,
                city,
                industry
        );
    }

    private List<Lead> parseResponse(
            String body,
            String city,
            String industry) {

        List<Lead> leads =
                new ArrayList<>();

        try {

            JsonNode root =
                    mapper.readTree(body);

            JsonNode elements =
                    root.path("elements");

            if (!elements.isArray()) {
                return leads;
            }

            for (JsonNode element :
                    elements) {

                JsonNode tags =
                        element.path("tags");

                String name =
                        tag(tags, "name");

                if (name == null ||
                        name.isBlank()) {

                    continue;
                }

                Lead lead =
                        new Lead();

                lead.setBusiness(name);

                lead.setCategory(
                        industry
                                .trim()
                                .toLowerCase()
                );

                lead.setLocation(city);

                lead.setSource(
                        "OpenStreetMap"
                );

                lead.setWebsite(
                        first(
                                tag(tags, "website"),
                                tag(
                                        tags,
                                        "contact:website"
                                ),
                                tag(tags, "url")
                        )
                );

                lead.setEmail(
                        first(
                                tag(tags, "email"),
                                tag(
                                        tags,
                                        "contact:email"
                                )
                        )
                );

                lead.setPhone(
                        first(
                                tag(tags, "phone"),
                                tag(
                                        tags,
                                        "contact:phone"
                                ),
                                tag(tags, "mobile")
                        )
                );

                lead.setInstagram(
                        first(
                                tag(
                                        tags,
                                        "instagram"
                                ),
                                tag(
                                        tags,
                                        "contact:instagram"
                                )
                        )
                );

                leads.add(lead);
            }

            return removeDuplicates(leads);

        } catch (Exception e) {

            throw new RuntimeException(
                    "Invalid Overpass response: " +
                            e.getMessage(),
                    e
            );
        }
    }

    private String getFilter(
            String industry) {

        String value =
                industry
                        .trim()
                        .toLowerCase();

        return switch (value) {

            case "cafe", "cafes",
                 "coffee shop",
                 "coffee shops" ->
                    "[\"amenity\"=\"cafe\"]";

            case "restaurant", "restaurants" ->
                    "[\"amenity\"=\"restaurant\"]";

            case "fast food",
                 "fastfood",
                 "fast_food" ->
                    "[\"amenity\"=\"fast_food\"]";

            case "bar", "bars" ->
                    "[\"amenity\"=\"bar\"]";

            case "pub", "pubs" ->
                    "[\"amenity\"=\"pub\"]";

            case "bank", "banks" ->
                    "[\"amenity\"=\"bank\"]";

            case "school", "schools" ->
                    "[\"amenity\"=\"school\"]";

            case "college", "colleges" ->
                    "[\"amenity\"=\"college\"]";

            case "university",
                 "universities" ->
                    "[\"amenity\"=\"university\"]";

            case "hospital", "hospitals" ->
                    "[\"amenity\"=\"hospital\"]";

            case "clinic", "clinics" ->
                    "[\"amenity\"=\"clinic\"]";

            case "pharmacy", "pharmacies" ->
                    "[\"amenity\"=\"pharmacy\"]";

            case "cinema", "cinemas" ->
                    "[\"amenity\"=\"cinema\"]";

            case "gym", "gyms",
                 "fitness",
                 "fitness center",
                 "fitness centre" ->
                    "[\"leisure\"=\"fitness_centre\"]";

            case "hotel", "hotels" ->
                    "[\"tourism\"=\"hotel\"]";

            case "salon", "salons",
                 "hair salon",
                 "beauty salon" ->
                    "[\"shop\"=\"hairdresser\"]";

            case "supermarket",
                 "supermarkets" ->
                    "[\"shop\"=\"supermarket\"]";

            case "clothing",
                 "clothes",
                 "clothing store" ->
                    "[\"shop\"=\"clothes\"]";

            case "electronics",
                 "electronics store" ->
                    "[\"shop\"=\"electronics\"]";

            case "bakery", "bakeries" ->
                    "[\"shop\"=\"bakery\"]";

            case "fuel",
                 "petrol pump",
                 "petrol pumps",
                 "gas station" ->
                    "[\"amenity\"=\"fuel\"]";

            case "car",
                 "car dealer",
                 "car dealers" ->
                    "[\"shop\"=\"car\"]";

            case "jewellery",
                 "jewelry",
                 "jewellery store" ->
                    "[\"shop\"=\"jewelry\"]";

            case "furniture",
                 "furniture store" ->
                    "[\"shop\"=\"furniture\"]";

            case "mobile",
                 "mobile shop",
                 "mobile stores" ->
                    "[\"shop\"=\"mobile_phone\"]";

            case "books",
                 "book store",
                 "bookstore" ->
                    "[\"shop\"=\"books\"]";

            case "pet",
                 "pet shop",
                 "pet store" ->
                    "[\"shop\"=\"pet\"]";

            default ->
                    "[\"name\"~\"" +
                            escapeRegex(value) +
                            "\",i]";
        };
    }

    private double[] getCoordinates(
            String city) {

        try {

            String url =
                    "https://nominatim.openstreetmap.org/search?q=" +
                            URLEncoder.encode(
                                    city.trim(),
                                    StandardCharsets.UTF_8
                            ) +
                            "&format=json" +
                            "&limit=1" +
                            "&countrycodes=in";

            HttpHeaders headers =
                    new HttpHeaders();

            headers.set(
                    "User-Agent",
                    "RubiumAI-LeadEngine/1.0"
            );

            ResponseEntity<String> response =
                    restTemplate.exchange(
                            url,
                            HttpMethod.GET,
                            new HttpEntity<>(headers),
                            String.class
                    );

            String body =
                    response.getBody();

            if (body == null ||
                    body.isBlank()) {

                throw new RuntimeException(
                        "Empty Nominatim response"
                );
            }

            JsonNode result =
                    mapper.readTree(body);

            if (!result.isArray() ||
                    result.isEmpty()) {

                throw new RuntimeException(
                        "City not found: " +
                                city
                );
            }

            JsonNode first =
                    result.get(0);

            double latitude =
                    first.path("lat")
                            .asDouble();

            double longitude =
                    first.path("lon")
                            .asDouble();

            System.out.println(
                    "📍 " +
                            city +
                            " -> " +
                            latitude +
                            ", " +
                            longitude
            );

            return new double[]{
                    latitude,
                    longitude
            };

        } catch (Exception e) {

            throw new RuntimeException(
                    "Could not find city: " +
                            city +
                            " -> " +
                            e.getMessage(),
                    e
            );
        }
    }

    private String tag(
            JsonNode tags,
            String key) {

        String value =
                tags.path(key)
                        .asText(null);

        if (value == null ||
                value.isBlank()) {

            return null;
        }

        return value.trim();
    }

    private String first(
            String... values) {

        for (String value : values) {

            if (value != null &&
                    !value.isBlank()) {

                return value.trim();
            }
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

            String key =
                    lead.getBusiness()
                            .trim()
                            .toLowerCase() +
                            "|" +
                            lead.getLocation()
                                    .trim()
                                    .toLowerCase();

            if (seen.add(key)) {
                result.add(lead);
            }
        }

        return result;
    }

    private String escapeRegex(
            String value) {

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}