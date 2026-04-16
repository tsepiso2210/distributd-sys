package com.distributedemail.client.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import okhttp3.*;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * ApiService - HTTP client for calling the email-api-service REST endpoints.
 *
 * The JavaFX client communicates with the backend via REST API calls.
 * This service abstracts all HTTP communication.
 *
 * Assignment requirement: "GUI may call backend APIs instead of directly using Kafka"
 */
public class ApiService {

    private static final ApiService INSTANCE = new ApiService();
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private String baseUrl;

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private ApiService() {
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());

        this.baseUrl = com.distributedemail.client.MainApp.API_BASE_URL;
    }

    public static ApiService getInstance() {
        return INSTANCE;
    }

    public void setBaseUrl(String url) {
        this.baseUrl = url;
    }

    /** HTTP GET - returns parsed JSON node */
    public JsonNode get(String path) throws IOException {
        Request request = new Request.Builder()
            .url(baseUrl + path)
            .get()
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "{}";
            if (!response.isSuccessful()) {
                throw new IOException("GET " + path + " failed: " + response.code() + " - " + body);
            }
            return objectMapper.readTree(body);
        }
    }

    /** HTTP POST with JSON body - returns parsed JSON node */
    public JsonNode post(String path, Object payload) throws IOException {
        String json = objectMapper.writeValueAsString(payload);
        RequestBody body = RequestBody.create(json, JSON);

        Request request = new Request.Builder()
            .url(baseUrl + path)
            .post(body)
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "{}";
            if (!response.isSuccessful()) {
                throw new IOException("POST " + path + " failed: " + response.code() + " - " + responseBody);
            }
            return objectMapper.readTree(responseBody);
        }
    }

    /** HTTP PUT with JSON body */
    public JsonNode put(String path, Object payload) throws IOException {
        String json = objectMapper.writeValueAsString(payload);
        RequestBody body = RequestBody.create(json, JSON);

        Request request = new Request.Builder()
            .url(baseUrl + path)
            .put(body)
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "{}";
            if (!response.isSuccessful()) {
                throw new IOException("PUT " + path + " failed: " + response.code() + " - " + responseBody);
            }
            return objectMapper.readTree(responseBody);
        }
    }

    /** HTTP DELETE */
    public void delete(String path) throws IOException {
        Request request = new Request.Builder()
            .url(baseUrl + path)
            .delete()
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("DELETE " + path + " failed: " + response.code());
            }
        }
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}
