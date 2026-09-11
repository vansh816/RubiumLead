package com.RubiumLead.Entity;

public class Lead {

    private String business;
    private String category;
    private String location;

    private String website;
    private String email;
    private String instagram;
    private String phone;

    private String source;

    private double websiteScore;
    private double brandScore;
    private double hfFitScore;

    private Priority priority;
    private RecommendedOffer recommendedOffer;

    private String problemFound;
    private String pitchAngle;
    private String lastChecked;


    public String getBusiness() {
        return business;
    }

    public void setBusiness(String business) {
        this.business = business;
    }


    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }


    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }


    public String getWebsite() {
        return website;
    }

    public void setWebsite(String website) {
        this.website = website;
    }


    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }


    public String getInstagram() {
        return instagram;
    }

    public void setInstagram(String instagram) {
        this.instagram = instagram;
    }


    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }


    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }


    public double getWebsiteScore() {
        return websiteScore;
    }

    public void setWebsiteScore(double websiteScore) {
        this.websiteScore = websiteScore;
    }


    public double getBrandScore() {
        return brandScore;
    }

    public void setBrandScore(double brandScore) {
        this.brandScore = brandScore;
    }


    public double getHfFitScore() {
        return hfFitScore;
    }

    public void setHfFitScore(double hfFitScore) {
        this.hfFitScore = hfFitScore;
    }


    public Priority getPriority() {
        return priority;
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
    }


    public RecommendedOffer getRecommendedOffer() {
        return recommendedOffer;
    }

    public void setRecommendedOffer(
            RecommendedOffer recommendedOffer) {

        this.recommendedOffer = recommendedOffer;
    }


    public String getProblemFound() {
        return problemFound;
    }

    public void setProblemFound(String problemFound) {
        this.problemFound = problemFound;
    }


    public String getPitchAngle() {
        return pitchAngle;
    }

    public void setPitchAngle(String pitchAngle) {
        this.pitchAngle = pitchAngle;
    }


    public String getLastChecked() {
        return lastChecked;
    }

    public void setLastChecked(String lastChecked) {
        this.lastChecked = lastChecked;
    }
}