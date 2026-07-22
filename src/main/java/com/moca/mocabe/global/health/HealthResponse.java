package com.moca.mocabe.global.health;

public class HealthResponse {
    private final String status;

    public HealthResponse(String status) {
        this.status = status;
    }

    public String getStatus() {
        return status;
    }
}
