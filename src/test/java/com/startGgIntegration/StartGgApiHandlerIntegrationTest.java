package com.startGgIntegration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.Optional;
import java.util.ArrayList;
import java.util.List;

import com.startGgIntegration.StartGgApiHandler.RequestType;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = com.tournamentviz.TournamentVizBackendApplication.class)
    class StartGgApiHandlerIntegrationTest {

        @Autowired
        StartGgApiHandler handler;
        @Autowired
        StartGgHttpRequest httpRequest;

        // fake repo for testing!!
        @TestConfiguration
        static class TestConfig {
            @Bean
            public EventImportRepo eventImportRepo() {
                return new FakeRepo();
            }
        }

        static class FakeRepo implements EventImportRepo {
            List<EventImport> store = new ArrayList<>();

            @Override
            public EventImport save(EventImport eventImport) {
                store.add(eventImport);
                return eventImport;
            }

            @Override
            public Optional<EventImport> findById(int id) {
                return Optional.empty();
            }

            @Override
            public void delete(int id) {}
        }

        @Test
        void importEvent_startGgApi_CompletedEvent() {
            String result = handler.importEvent(
                "https://www.start.gg/tournament/genesis-x3/event/melee-singles",
                1
            );

            System.out.println("Result: " + result);
            assertTrue(result.startsWith("Import complete"), "Expected success but got: " + result);
            
        }

        @Test
        void importEvent_startGgApi_UpcomingEvent() {
            String result = handler.importEvent(
                "https://www.start.gg/tournament/forgotten-faceoff-2/event/melee-singles",
                1
            );

            System.out.println("Result: " + result);
            assertTrue(result.startsWith("Import complete"), "Expected success but got: " + result);
        }

        @Test
        void importEvent_startGgApi_BadLink(){ //Should catch that the link is bad
            String result = handler.importEvent(
                "https://www.start.gg",
                1
            );

            System.out.println("Result: " + result);
            assertTrue(result.startsWith("Import failed: Invalid Start.gg link!"));
        }

        @Test
        void rateLimiter_tooManyRequests_fallbackFires() {
            
            final int requestCount = 300; // do a bunch of requests in a row to make sure the start gg api doesnt return rate limit errors
            int successCount = 0;
            for(int i = 0; i < requestCount; i++){
                String myJson = "";
                try{
                    myJson = httpRequest.makeStartGgRequest(handler.formatUrl_toSlug("https://www.start.gg/tournament/genesis-x3/event/melee-singles"), RequestType.EVENTINFO,1,50);
                }catch(Exception e){
                    System.out.println("===Exception encountered: "+e.getMessage());
                    myJson = "Import failed: "+e.getMessage();
                }
                if (myJson.contains("{\"data\"")) {
                    successCount++;
                } else {
                    System.out.println("=== FAILED RESULT " + i + ": " + myJson);  // debug
                }
            }
            System.out.println("===Final result: " + successCount + " successes");
            assertTrue(successCount == requestCount, "Expected graceful handling of rate limits, but had " + (requestCount-successCount) + " failures");
        }
    }