package com.RubiumLead.Service;

import com.RubiumLead.Entity.Lead;
import com.RubiumLead.Entity.Priority;
import com.RubiumLead.Entity.RecommendedOffer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class EnrichmentService {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile(
                    "[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}",
                    Pattern.CASE_INSENSITIVE
            );

    private static final Pattern PHONE_PATTERN =
            Pattern.compile(
                    "(?:\\+91[\\s-]?)?[6-9]\\d{9}"
            );

    public List<Lead> enrich(List<Lead> input) {

        List<Lead> leads =
                new ArrayList<>(input);

        for (Lead lead : leads) {

            try {

                if (lead.getWebsite() != null &&
                        !lead.getWebsite().isBlank()) {

                    enrichWebsite(lead);

                } else {

                    lead.setWebsiteScore(0);

                    lead.setBrandScore(
                            lead.getInstagram() != null
                                    ? 60
                                    : 20
                    );
                }

                scoreLead(lead);

            } catch (Exception e) {

                System.out.println(
                        "⚠️ Enrichment failed for " +
                                lead.getBusiness() +
                                ": " +
                                e.getMessage()
                );

                scoreLead(lead);
            }

            lead.setLastChecked(
                    LocalDateTime.now().toString()
            );
        }

        return leads;
    }

    private void enrichWebsite(
            Lead lead) {

        String website =
                normalizeUrl(
                        lead.getWebsite()
                );

        lead.setWebsite(website);

        try {

            Document document =
                    Jsoup.connect(website)
                            .userAgent(
                                    "Mozilla/5.0 " +
                                            "RubiumAI Lead Engine"
                            )
                            .timeout(15000)
                            .followRedirects(true)
                            .get();

            extractContacts(
                    document,
                    lead
            );

            lead.setWebsiteScore(
                    calculateWebsiteScore(
                            document,
                            lead
                    )
            );

            lead.setBrandScore(
                    calculateBrandScore(
                            document,
                            lead
                    )
            );

        } catch (Exception e) {

            lead.setWebsiteScore(25);

            lead.setBrandScore(
                    lead.getInstagram() != null
                            ? 60
                            : 20
            );
        }
    }

    private void extractContacts(
            Document document,
            Lead lead) {

        String html =
                document.html();

        if (lead.getEmail() == null ||
                lead.getEmail().isBlank()) {

            Matcher matcher =
                    EMAIL_PATTERN.matcher(html);

            if (matcher.find()) {
                lead.setEmail(
                        matcher.group()
                );
            }
        }

        if (lead.getPhone() == null ||
                lead.getPhone().isBlank()) {

            Elements phoneLinks =
                    document.select(
                            "a[href^=tel:]"
                    );

            if (!phoneLinks.isEmpty()) {

                String phone =
                        phoneLinks
                                .first()
                                .attr("href")
                                .replace(
                                        "tel:",
                                        ""
                                );

                lead.setPhone(phone);
            }
        }

        if (lead.getPhone() == null ||
                lead.getPhone().isBlank()) {

            Matcher matcher =
                    PHONE_PATTERN.matcher(html);

            if (matcher.find()) {
                lead.setPhone(
                        matcher.group()
                );
            }
        }

        if (lead.getInstagram() == null ||
                lead.getInstagram().isBlank()) {

            Elements links =
                    document.select(
                            "a[href*=instagram.com]"
                    );

            if (!links.isEmpty()) {

                lead.setInstagram(
                        links.first().attr("href")
                );
            }
        }
    }

    private double calculateWebsiteScore(
            Document document,
            Lead lead) {

        double score = 25;

        if (!document.title().isBlank()) {
            score += 10;
        }

        if (document.select(
                "meta[name=viewport]"
        ).size() > 0) {
            score += 15;
        }

        if (document.select("h1").size() > 0) {
            score += 10;
        }

        if (document.select("nav").size() > 0) {
            score += 10;
        }

        if (document.select("img").size() > 0) {
            score += 5;
        }

        if (lead.getEmail() != null &&
                !lead.getEmail().isBlank()) {
            score += 10;
        }

        if (lead.getPhone() != null &&
                !lead.getPhone().isBlank()) {
            score += 5;
        }

        if (document.html().length() > 5000) {
            score += 10;
        }

        return Math.min(100, score);
    }

    private double calculateBrandScore(
            Document document,
            Lead lead) {

        double score = 30;

        if (lead.getInstagram() != null &&
                !lead.getInstagram().isBlank()) {

            score += 30;
        }

        if (document.select("img").size() > 0) {
            score += 15;
        }

        for (Element img :
                document.select("img")) {

            String alt =
                    img.attr("alt");

            if (alt.toLowerCase()
                    .contains(
                            lead.getBusiness()
                                    .toLowerCase()
                    )) {

                score += 15;
                break;
            }
        }

        String html =
                document.html()
                        .toLowerCase();

        if (html.contains("facebook.com") ||
                html.contains("linkedin.com")) {

            score += 10;
        }

        return Math.min(100, score);
    }

    private void scoreLead(
            Lead lead) {

        double score =
                lead.getWebsiteScore() * 0.45
                        + lead.getBrandScore() * 0.25;

        if (lead.getEmail() != null &&
                !lead.getEmail().isBlank()) {

            score += 10;
        }

        if (lead.getInstagram() != null &&
                !lead.getInstagram().isBlank()) {

            score += 10;
        }

        if (lead.getWebsite() == null ||
                lead.getWebsite().isBlank()) {

            score += 8;

        } else {

            score += 5;
        }

        lead.setHfFitScore(
                Math.min(100, score)
        );

        if (lead.getWebsite() == null ||
                lead.getWebsite().isBlank()) {

            lead.setRecommendedOffer(
                    RecommendedOffer.BUSINESS_WEBSITE
            );

            lead.setProblemFound(
                    "Business does not have a detected website"
            );

            lead.setPitchAngle(
                    "Offer a professional business website"
            );

        } else if (
                lead.getWebsiteScore() < 65) {

            lead.setRecommendedOffer(
                    RecommendedOffer.WEBSITE_REFRESH
            );

            lead.setProblemFound(
                    "Website quality can be improved"
            );

            lead.setPitchAngle(
                    "Offer website redesign and performance improvements"
            );

        } else if (
                lead.getBrandScore() < 65) {

            lead.setRecommendedOffer(
                    RecommendedOffer.BRAND_WEBSITE
            );

            lead.setProblemFound(
                    "Brand presence can be improved"
            );

            lead.setPitchAngle(
                    "Offer stronger branding and digital presence"
            );

        } else {

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

        if (lead.getHfFitScore() >= 70) {

            lead.setPriority(
                    Priority.A_PITCH
            );

        } else if (
                lead.getHfFitScore() >= 45) {

            lead.setPriority(
                    Priority.B_REVIEW
            );

        } else {

            lead.setPriority(
                    Priority.C_SKIP
            );
        }
    }

    private String normalizeUrl(
            String url) {

        if (url == null ||
                url.isBlank()) {

            return null;
        }

        url = url.trim();

        if (!url.startsWith("http://") &&
                !url.startsWith("https://")) {

            url = "https://" + url;
        }

        return url;
    }
}