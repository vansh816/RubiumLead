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
import java.util.List;

@Service
public class OverpassService {

    private final RestTemplate restTemplate;
    private final ObjectMapper mapper;

    private final List<String> overpassServers = List.of(
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

        if (industry == null || industry.isBlank()) {
            throw new RuntimeException("Industry is required");
        }

        if (city == null || city.isBlank()) {
            throw new RuntimeException("City is required");
        }

        double[] coordinates = getCoordinates(city);

        String filter = getFilter(industry);

        /*
         * 15 KM radius
         *
         * This gives much better coverage for cities
         * like Gurgaon where businesses are spread out.
         */
        String query = """
                [out:json][timeout:30];
                (
                  nwr%s(around:15000,%s,%s);
                );
                out center tags;
                """.formatted(
                filter,
                coordinates[0],
                coordinates[1]
        );

        Exception lastException = null;

        for (String server : overpassServers) {

            try {

                System.out.println(
                        "🌍 Trying Overpass server: " + server
                );

                List<Lead> leads =
                        executeOverpassQuery(
                                server,
                                query,
                                city,
                                industry
                        );

                System.out.println(
                        "✅ Overpass found "
                                + leads.size()
                                + " leads for "
                                + industry
                                + " in "
                                + city
                );

                return leads;

            } catch (Exception e) {

                lastException = e;

                System.out.println(
                        "⚠️ Overpass failed: "
                                + server
                                + " -> "
                                + e.getMessage()
                );
            }
        }

        throw new RuntimeException(
                "All Overpass servers failed. Last error: "
                        + (lastException != null
                        ? lastException.getMessage()
                        : "unknown")
        );
    }


    private List<Lead> executeOverpassQuery(
            String server,
            String query,
            String city,
            String industry) {

        HttpHeaders headers = new HttpHeaders();

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

        ResponseEntity<String> response =
                restTemplate.postForEntity(
                        server,
                        new HttpEntity<>(body, headers),
                        String.class
                );

        if (response.getBody() == null
                || response.getBody().isBlank()) {

            throw new RuntimeException(
                    "Empty response from Overpass"
            );
        }

        return parseLeads(
                response.getBody(),
                city,
                industry
        );
    }


    private List<Lead> parseLeads(
            String responseBody,
            String city,
            String industry) {

        try {

            JsonNode elements =
                    mapper.readTree(responseBody)
                            .path("elements");

            List<Lead> leads = new ArrayList<>();

            if (!elements.isArray()) {
                return leads;
            }

            for (JsonNode element : elements) {

                JsonNode tags =
                        element.path("tags");

                String name =
                        tag(tags, "name");

                if (name == null || name.isBlank()) {
                    continue;
                }

                Lead lead = new Lead();

                lead.setBusiness(name);

                lead.setCategory(
                        industry.trim().toLowerCase()
                );

                lead.setLocation(city);

                /*
                 * WEBSITE
                 */
                lead.setWebsite(
                        first(
                                tag(tags, "website"),
                                tag(tags, "contact:website"),
                                tag(tags, "url"),
                                tag(tags, "url:official")
                        )
                );

                /*
                 * EMAIL
                 */
                lead.setEmail(
                        first(
                                tag(tags, "email"),
                                tag(tags, "contact:email")
                        )
                );

                /*
                 * PHONE
                 */
                lead.setPhone(
                        first(
                                tag(tags, "phone"),
                                tag(tags, "contact:phone"),
                                tag(tags, "mobile"),
                                tag(tags, "contact:mobile")
                        )
                );

                /*
                 * INSTAGRAM
                 */
                lead.setInstagram(
                        first(
                                tag(tags, "instagram"),
                                tag(tags, "contact:instagram")
                        )
                );

                lead.setSource("OpenStreetMap");

                leads.add(lead);
            }

            return leads;

        } catch (Exception e) {

            throw new RuntimeException(
                    "Could not parse Overpass response: "
                            + e.getMessage(),
                    e
            );
        }
    }


    /*
     * Industry → OpenStreetMap filters
     */
    private String getFilter(
            String industry) {

        String value =
                industry
                        .trim()
                        .toLowerCase();

        return switch (value) {

            case "cafe", "cafes", "coffee shop",
                 "coffee shops" ->
                    "[\"amenity\"=\"cafe\"]";

            case "restaurant", "restaurants" ->
                    "[\"amenity\"=\"restaurant\"]";

            case "fast food", "fast_food",
                 "fastfood" ->
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

            case "university", "universities" ->
                    "[\"amenity\"=\"university\"]";

            case "hospital", "hospitals" ->
                    "[\"amenity\"=\"hospital\"]";

            case "clinic", "clinics" ->
                    "[\"amenity\"=\"clinic\"]";

            case "pharmacy", "pharmacies" ->
                    "[\"amenity\"=\"pharmacy\"]";

            case "cinema", "cinemas",
                 "movie theatre", "movie theater" ->
                    "[\"amenity\"=\"cinema\"]";

            case "gym", "gyms",
                 "fitness", "fitness center",
                 "fitness centre" ->
                    "[\"leisure\"=\"fitness_centre\"]";

            case "hotel", "hotels" ->
                    "[\"tourism\"=\"hotel\"]";

            case "salon", "salons",
                 "hair salon", "beauty salon" ->
                    "[\"shop\"=\"hairdresser\"]";

            case "supermarket", "supermarkets" ->
                    "[\"shop\"=\"supermarket\"]";

            case "clothing", "clothes",
                 "clothing store" ->
                    "[\"shop\"=\"clothes\"]";

            case "electronics",
                 "electronics store" ->
                    "[\"shop\"=\"electronics\"]";

            case "bakery", "bakeries" ->
                    "[\"shop\"=\"bakery\"]";

            case "fuel", "petrol pump",
                 "petrol pumps", "gas station" ->
                    "[\"amenity\"=\"fuel\"]";

            case "car", "car dealer",
                 "car dealers" ->
                    "[\"shop\"=\"car\"]";

            case "jewellery", "jewelry",
                 "jewellery store" ->
                    "[\"shop\"=\"jewelry\"]";

            case "furniture", "furniture store" ->
                    "[\"shop\"=\"furniture\"]";

            case "mobile", "mobile shop",
                 "mobile stores" ->
                    "[\"shop\"=\"mobile_phone\"]";

            case "books", "book store",
                 "bookstore" ->
                    "[\"shop\"=\"books\"]";

            case "pet", "pet shop",
                 "pet store" ->
                    "[\"shop\"=\"pet\"]";

            case "bakery shop" ->
                    "[\"shop\"=\"bakery\"]";

            /*
             * Unknown industry:
             * search generic businesses instead of
             * returning nothing.
             */
            default ->
                    "[\"name\"]";
        };
    }


    /*
     * City → Latitude / Longitude
     */
    private double[] getCoordinates(
            String city) {

        try {

            String url =
                    "https://nominatim.openstreetmap.org/search?q="
                            + URLEncoder.encode(
                            city.trim(),
                            StandardCharsets.UTF_8
                    )
                            + "&format=json"
                            + "&limit=1"
                            + "&countrycodes=in";

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

            if (response.getBody() == null
                    || response.getBody().isBlank()) {

                throw new RuntimeException(
                        "Empty response from Nominatim"
                );
            }

            JsonNode result =
                    mapper.readTree(
                            response.getBody()
                    );

            if (!result.isArray()
                    || result.isEmpty()) {

                throw new RuntimeException(
                        "City not found: " + city
                );
            }

            JsonNode firstResult =
                    result.get(0);

            double latitude =
                    firstResult
                            .get("lat")
                            .asDouble();

            double longitude =
                    firstResult
                            .get("lon")
                            .asDouble();

            System.out.println(
                    "📍 "
                            + city
                            + " -> "
                            + latitude
                            + ", "
                            + longitude
            );

            return new double[]{
                    latitude,
                    longitude
            };

        } catch (Exception e) {

            throw new RuntimeException(
                    "Could not find city: "
                            + city
                            + " -> "
                            + e.getMessage(),
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

        if (value == null
                || value.isBlank()) {

            return null;
        }

        return value.trim();
    }


    private String first(
            String... values) {

        for (String value : values) {

            if (value != null
                    && !value.isBlank()) {

                return value.trim();
            }
        }

        return null;
    }
}