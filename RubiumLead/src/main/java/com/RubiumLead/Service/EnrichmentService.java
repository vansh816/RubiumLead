package com.RubiumLead.Service;

import com.RubiumLead.Entity.Lead;
import com.RubiumLead.Entity.Priority;
import com.RubiumLead.Entity.RecommendedOffer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class EnrichmentService {

    private static final Pattern EMAIL =
            Pattern.compile(
                    "[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}",
                    Pattern.CASE_INSENSITIVE
            );


    private static final Pattern PHONE =
            Pattern.compile(
                    "(?:\\+91[\\s-]?)?[6-9]\\d{9}"
            );


    public List<Lead> enrich(
            List<Lead> leads) {


        for (Lead lead : leads) {

            if (hasValue(
                    lead.getWebsite()
            )) {

                enrichWebsite(lead);

            } else {

                lead.setWebsiteScore(0);

                lead.setBrandScore(
                        hasValue(
                                lead.getInstagram()
                        )
                                ? 60
                                : 20
                );
            }


            scoreLead(lead);
        }


        return leads;
    }


    private void enrichWebsite(
            Lead lead) {

        try {

            String url =
                    lead.getWebsite();


            if (!url.startsWith("http")) {

                url =
                        "https://" + url;
            }


            lead.setWebsite(url);


            Document doc =
                    Jsoup.connect(url)
                            .userAgent(
                                    "Mozilla/5.0 " +
                                    "(Windows NT 10.0; Win64; x64) " +
                                    "AppleWebKit/537.36 " +
                                    "Chrome/120 Safari/537.36"
                            )
                            .timeout(15000)
                            .followRedirects(true)
                            .get();


            String html =
                    doc.html();


            // CONTACT EXTRACTION
            extractContactDetails(
                    lead,
                    doc,
                    html
            );


            // SCORING
            lead.setWebsiteScore(
                    websiteScore(
                            lead,
                            doc,
                            html
                    )
            );


            lead.setBrandScore(
                    brandScore(
                            lead,
                            doc
                    )
            );


            System.out.println(
                    "✅ Enriched: "
                            + lead.getBusiness()
            );


        } catch (Exception e) {

            System.out.println(
                    "⚠️ Could not open website for "
                            + lead.getBusiness()
                            + ": "
                            + e.getMessage()
            );


            /*
             * Website exists but couldn't be
             * scraped.
             */
            lead.setWebsiteScore(25);


            lead.setBrandScore(
                    hasValue(
                            lead.getInstagram()
                    )
                            ? 60
                            : 20
            );
        }
    }


    private void extractContactDetails(
            Lead lead,
            Document doc,
            String html) {


        // EMAIL
        if (!hasValue(
                lead.getEmail()
        )) {

            String email =
                    findEmail(html);


            if (email != null) {

                lead.setEmail(
                        cleanEmail(email)
                );
            }
        }


        // PHONE
        if (!hasValue(
                lead.getPhone()
        )) {

            String phone =

                    doc.select(
                            "a[href^=tel:]"
                    )
                    .stream()
                    .map(
                            e ->
                                    e.attr(
                                            "href"
                                    )
                                    .replaceFirst(
                                            "(?i)^tel:",
                                            ""
                                    )
                                    .trim()
                    )
                    .filter(
                            this::hasValue
                    )
                    .findFirst()
                    .orElse(
                            findPhone(html)
                    );


            if (phone != null) {

                lead.setPhone(
                        cleanPhone(phone)
                );
            }
        }


        // INSTAGRAM
        if (!hasValue(
                lead.getInstagram()
        )) {

            String instagram =

                    doc.select(
                            "a[href*=instagram.com]"
                    )
                    .stream()
                    .map(
                            e ->
                                    e.attr(
                                            "abs:href"
                                    )
                    )
                    .filter(
                            this::isInstagramUrl
                    )
                    .findFirst()
                    .orElse(null);


            if (instagram != null) {

                lead.setInstagram(
                        instagram
                );
            }
        }
    }


    private String findEmail(
            String html) {

        Matcher matcher =
                EMAIL.matcher(html);


        return matcher.find()
                ? matcher.group()
                : null;
    }


    private String findPhone(
            String html) {

        Matcher matcher =
                PHONE.matcher(html);


        return matcher.find()
                ? matcher.group()
                : null;
    }


    private String cleanEmail(
            String email) {

        if (email == null) {
            return null;
        }


        return email
                .replace(
                        "mailto:",
                        ""
                )
                .trim();
    }


    private String cleanPhone(
            String phone) {

        if (phone == null) {
            return null;
        }


        return phone
                .replaceAll(
                        "[^0-9+]",
                        ""
                );
    }


    private boolean isInstagramUrl(
            String url) {

        return hasValue(url)
                && url
                .toLowerCase()
                .contains(
                        "instagram.com/"
                );
    }


    private double websiteScore(
            Lead lead,
            Document doc,
            String html) {

        double score = 25;


        if (!doc.title().isBlank()) {
            score += 10;
        }


        if (!doc.select(
                "meta[name=viewport]"
        ).isEmpty()) {

            score += 15;
        }


        if (!doc.select("h1").isEmpty()) {
            score += 10;
        }


        if (!doc.select("nav").isEmpty()) {
            score += 10;
        }


        if (!doc.select("img").isEmpty()) {
            score += 5;
        }


        if (hasValue(
                lead.getEmail()
        )) {

            score += 10;
        }


        if (hasValue(
                lead.getPhone()
        )) {

            score += 5;
        }


        if (html.length() > 5000) {
            score += 10;
        }


        return clamp(score);
    }


    private double brandScore(
            Lead lead,
            Document doc) {

        double score = 30;


        if (hasValue(
                lead.getInstagram()
        )) {

            score += 30;
        }


        if (!doc.select(
                "img"
        ).isEmpty()) {

            score += 15;
        }


        if (!doc.select(
                "img[alt*=logo i]"
        ).isEmpty()) {

            score += 15;
        }


        if (!doc.select(
                "a[href*=facebook.com], " +
                                "a[href*=linkedin.com]"
        ).isEmpty()) {

            score += 10;
        }


        return clamp(score);
    }


    private void scoreLead(
            Lead lead) {

        double score =

                lead.getWebsiteScore() * 0.45
                        + lead.getBrandScore() * 0.25;


        if (hasValue(
                lead.getEmail()
        )) {

            score += 10;
        }


        if (hasValue(
                lead.getInstagram()
        )) {

            score += 10;
        }


        if (!hasValue(
                lead.getWebsite()
        )) {

            score += 8;

        } else {

            score += 5;
        }


        lead.setHfFitScore(
                clamp(score)
        );


        // NO WEBSITE
        if (!hasValue(
                lead.getWebsite()
        )) {

            lead.setRecommendedOffer(
                    RecommendedOffer
                            .BUSINESS_WEBSITE
            );


            lead.setProblemFound(
                    "Business does not have a verified website."
            );


            lead.setPitchAngle(
                    "Offer a professional business website."
            );


            lead.setPriority(
                    score >= 50
                            ? Priority.A_PITCH
                            : Priority.B_REVIEW
            );


            return;
        }


        // BAD WEBSITE
        if (lead.getWebsiteScore() < 65) {

            lead.setRecommendedOffer(
                    RecommendedOffer
                            .WEBSITE_REFRESH
            );


            lead.setProblemFound(
                    "Website needs improvement."
            );


            lead.setPitchAngle(
                    "Improve website UX and conversion."
            );


            lead.setPriority(
                    score >= 55
                            ? Priority.A_PITCH
                            : Priority.B_REVIEW
            );


            return;
        }


        // BRAND
        if (lead.getBrandScore() < 65) {

            lead.setRecommendedOffer(
                    RecommendedOffer
                            .BRAND_WEBSITE
            );


            lead.setProblemFound(
                    "Brand presence can be improved."
            );


            lead.setPitchAngle(
                    "Improve digital brand presence."
            );


            lead.setPriority(
                    score >= 55
                            ? Priority.A_PITCH
                            : Priority.B_REVIEW
            );


            return;
        }


        // GOOD LEAD
        lead.setRecommendedOffer(
                RecommendedOffer.SKIP
        );


        lead.setProblemFound(
                "No strong opportunity found."
        );


        lead.setPitchAngle("");


        lead.setPriority(
                Priority.C_SKIP
        );
    }


    private boolean hasValue(
            String value) {

        return value != null
                && !value.isBlank();
    }


    private double clamp(
            double value) {

        return Math.max(
                0,
                Math.min(
                        100,
                        Math.round(value)
                )
        );
    }
}