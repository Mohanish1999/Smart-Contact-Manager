package com.smart.dto;

import java.util.List;

import com.smart.entity.Contact;

/** Real numbers computed from the database for the dashboard. */
public record DashboardStats(long totalContacts, long withEmail, long withPhoto, List<Contact> recent) {

    // JavaBean-style getters so Thymeleaf/SpEL can read the values as ${stats.totalContacts} etc.
    public long getTotalContacts() {
        return totalContacts;
    }

    public long getWithEmail() {
        return withEmail;
    }

    public long getWithPhoto() {
        return withPhoto;
    }

    public List<Contact> getRecent() {
        return recent;
    }
}
