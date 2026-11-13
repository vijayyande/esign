package com.esign.common.http;

import com.esign.common.error.ApiException;
import com.esign.common.error.EsignErrorCode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public final class XmlHttp {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public String postXml(String url, String body) {
        return post(url, "application/xml; charset=UTF-8", body);
    }

    public String postJson(String url, String body) {
        return post(url, "application/json; charset=UTF-8", body);
    }

    private String post(String url, String contentType, String body) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", contentType)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                throw new ApiException(EsignErrorCode.UNKNOWN_ESIGN,
                        "HTTP " + response.statusCode() + " from " + url);
            }
            return response.body();
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(EsignErrorCode.UNKNOWN_ESIGN, exception.getMessage());
        }
    }
}
