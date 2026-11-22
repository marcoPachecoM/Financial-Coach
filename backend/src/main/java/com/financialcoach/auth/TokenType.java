package com.financialcoach.auth;

/**
 * Value of the {@code type} claim. Each endpoint accepts only one type: a refresh token is
 * rejected as an access token and vice versa.
 */
enum TokenType {
    ACCESS("access"),
    REFRESH("refresh");

    private final String claim;

    TokenType(String claim) {
        this.claim = claim;
    }

    String claim() {
        return claim;
    }
}
