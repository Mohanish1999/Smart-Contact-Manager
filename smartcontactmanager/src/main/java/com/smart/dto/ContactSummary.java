package com.smart.dto;

import com.smart.entity.Contact;

/** Safe JSON view of a contact for the search box: no owner, no password hash, no collections. */
public record ContactSummary(int cid, String name, String secondname, String email, String phone, String image) {

    public static ContactSummary from(Contact contact) {
        return new ContactSummary(contact.getCid(), contact.getName(), contact.getSecondname(),
                contact.getEmail(), contact.getPhone(), contact.getImage());
    }
}
