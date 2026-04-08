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

@SpringBootTest(
        classes = {StartGgApiHandler.class, StartGgApiHandlerIntegrationTest.TestConfig.class},
        properties = { 
            "startgg.api-key = f07a2ce46c71c74a8ef6df213bd81499"
        }
    )
    class StartGgApiHandlerIntegrationTest {

        @Autowired
        StartGgApiHandler handler;

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
            
            final int requestLimits = 12 + 4; // this was set in application.yml, we add a few to hit rate limit
            String[] listofresults = new String[requestLimits]; 

            for(int i = 0; i < requestLimits; i++){
                String myJson = "";
                try{
                    myJson = handler.makeStartGgRequest(handler.formatUrl_toSlug("https://www.start.gg/tournament/genesis-x3/event/melee-singles"), RequestType.EVENTINFO,1,50);
                }catch(Exception e){
                    System.out.println("Exception encountered: "+e.getMessage());
                    myJson = "Import failed: "+e.getMessage();
                }
                listofresults[i] = myJson;
                System.out.println("Request returned: " + myJson);
            }

            String finalResult = listofresults[requestLimits - 1];
            System.out.println("Final result: " + finalResult);

            assertTrue(finalResult.contains("Start.gg API unavailable"), "Expected fallback message but got: " + finalResult);
        }

        @Test
        void circuitBreaker_repeatedFailures_circuitOpens() {
            // Point at a bad slug so every request fails
            for (int i = 0; i < 3; i++) {
                handler.importEvent("https://start.gg/tournament/this-does-not-exist-xyz/event/fake-event", 1);
            }

            long start = System.currentTimeMillis();
            String result = handler.importEvent("https://start.gg/tournament/this-does-not-exist-xyz/event/fake-event", 1);
            long duration = System.currentTimeMillis() - start;

            System.out.println("Result: " + result);
            System.out.println("Duration: " + duration + "ms");

            assertTrue(result.startsWith("Import failed"));
            assertTrue(duration < 500, "Expected fast-fail from open circuit but took: " + duration + "ms");
        }
    }