package com.tnb.dbp.customer;

/** Customer details safe to return to the logged-in customer (mobile is masked). */
public record CustomerProfile(String customerId, String fullName, String maskedMobile, String email) {
}
