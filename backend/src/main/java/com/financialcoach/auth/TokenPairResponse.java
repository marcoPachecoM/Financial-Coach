package com.financialcoach.auth;

/** {@code TokenPair} schema of openapi.yaml. */
record TokenPairResponse(String accessToken, String refreshToken) {

    @Override
    public String toString() {
        return "TokenPairResponse[***]";
    }
}
