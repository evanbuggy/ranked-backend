package com.startGgIntegration;

// authored by Liam Kelly, 22346317

import com.fasterxml.jackson.databind.ObjectMapper;
import com.startGgIntegration.StartGgApiHandler.RequestType;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.*;
import java.util.Map;

@Service
public class StartGgHttpRequest {
    @Value("${app.startgg.api-key}")
    private String apiKey;

    private static final String API_URL = "https://api.start.gg/gql/alpha";
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

        @CircuitBreaker(name = "startgg", fallbackMethod = "circuitBreakerFallback") // open circuit in case of repeated errors
        @Retry(name = "startgg", fallbackMethod = "circuitBreakerFallback") // retry on error
        @RateLimiter(name = "startgg", fallbackMethod = "rateLimitFallback") // rate limiting: start gg has a rate limit of 80 requests per minute, so we need to limit the amount of possible requests.
        protected String makeStartGgRequest(String slug, RequestType requesting, int pagenum, int perPage) {
            try {
                String query;
                String body;
                switch(requesting){
                    case EVENTINFO:
                        query = """
                                query getEventInfo($slug: String){
                                    event(slug:$slug){
                                        id
                                        name
                                        videogame{id,displayName}
                                        numEntrants
                                        startAt
                                        tournament{
                                            name
                                            id
                                        }
                                    }
                                }
                                """;
                        body = mapper.writeValueAsString(Map.of(
                        "query", query,
                        "variables", Map.of("slug", slug)
                        ));
                        break;
                    case ENTRANTS:
                        query = """
                                query getEventEntrants($slug: String, $page: Int, $perPage: Int) {
                                event(slug: $slug) {
                                    entrants(query: { page: $page, perPage: $perPage }) {
                                    pageInfo{
                                        totalPages
                                    }
                                    nodes {
                                        name
                                        id
                                        participants {
                                        user {
                                            id
                                        }
                                        }
                                    }
                                    }
                                }
                                }
                                """;
                        body = mapper.writeValueAsString(Map.of(
                            "query", query,
                            "variables", Map.of("slug", slug, "page", pagenum, "perPage", perPage)
                        ));
                        break;
                    case SETS:
                        query = """
                                query getEventSets($slug: String, $page: Int, $perPage: Int) {
                                event(slug: $slug) {
                                    sets(page: $page, perPage: $perPage) {
                                    pageInfo {
                                        totalPages
                                    }
                                    nodes {
                                        winnerId
                                        slots {
                                        entrant {
                                            id
                                            name
                                        }
                                        }
                                    }
                                    }
                                }
                                }
                                """;
                        body = mapper.writeValueAsString(Map.of(
                            "query", query,
                            "variables", Map.of("slug", slug, "page", pagenum, "perPage", perPage)
                        ));
                    break;
                    default:
                        throw new IllegalArgumentException("invalid request type");
                }
                var request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

                var response = http.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200)
                    throw new RuntimeException("API error: " + response.statusCode());

                return response.body();

            } catch (Exception e) {
                throw new RuntimeException("Start.gg request failed: " + e.getMessage(), e);
            }
        }
        protected String circuitBreakerFallback(String slug, RequestType type, int page, int perPage, Exception e) {
            return "Start.gg API unavailable. Try again later.";
        }
        protected String rateLimitFallback(String slug, RequestType type, int page, int perPage, Exception e) {
            return "Start.gg API rate limit exceeded. Try again later.";
        }
}

