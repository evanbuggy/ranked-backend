    package com.startGgIntegration;
//authored by Liam Kelly, 22346317


    import com.shared.entities.*;
    import com.fasterxml.jackson.databind.JsonNode;
    import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

    import java.util.*;
    import com.startGgIntegration.entities.*;
    import com.startGgIntegration.valueObjects.ImportStatus;
 

    @Service
    public class StartGgApiHandler {

        public final StartGgHttpRequest httpRequest;
        private final ObjectMapper mapper = new ObjectMapper();
        private final EventImportRepo repo;
        
        
        private static final int ENTRANTS_PERPAGECOUNT = 499;
        private static final int SETS_PERPAGECOUNT = 249;
        public enum RequestType{
            EVENTINFO,
            ENTRANTS,
            SETS
        }  

        
        private final ApplicationEventPublisher eventPublisher;
        public StartGgApiHandler(@Autowired(required = true)EventImportRepo repo, ApplicationEventPublisher eventPublisher, StartGgHttpRequest httpRequest) {
            this.repo = repo;
            this.eventPublisher = eventPublisher;
            this.httpRequest = httpRequest;
        }

        // Converts URL to start gg 'slug'  e.g. "https://start.gg/tournament/my-tournament/event/my-event" will give back "tournament/my-tournament/event/my-event"
        public String formatUrl_toSlug(String url) {
            if (url == null) return ""; // url should not be null
            var m = java.util.regex.Pattern.compile("(tournament/[^/?#]+/event/[^/?#]+)").matcher(url); // This is the pattern being used - [^/?#] means that it cannot contain the special characters /, ? or #.
            return m.find() ? m.group(1) : ""; // return only the segment we want, if the segment is missing then return an empty string to signify failure
        }
        // Import orchestrator
        public String importEvent(String url, int eventGroupId) {
            EventImport eventImport = null;
            try {
                eventImport = EventImport.createEvent(url, eventGroupId);
                if (eventImport.getStatus() == ImportStatus.FAILED){
                    throw new Exception(eventImport.getFailureCause());
                }

                repo.save(eventImport);
                eventImport.status_inprogress();
                repo.save(eventImport);

                String slug = formatUrl_toSlug(url);
                String eventInfo_JSON = httpRequest.makeStartGgRequest(slug, RequestType.EVENTINFO,0,0);

                //get the first page - if there are more pages, then follow-up requests will be needed.
                //This is necessary because of rate limits with start gg's API
                String pageOneSets_JSON = httpRequest.makeStartGgRequest(slug, RequestType.SETS,1,SETS_PERPAGECOUNT);
                int totalPages_sets = parseTotalPages(pageOneSets_JSON, "sets");

                String pageOneEntrants_JSON = httpRequest.makeStartGgRequest(slug, RequestType.ENTRANTS,1,ENTRANTS_PERPAGECOUNT);
                int totalPages_entrants = parseTotalPages(pageOneEntrants_JSON, "entrants");

                StartGgEvent myEvent = parseEvent(eventInfo_JSON);
                List<Entrant> entrants = parseEntrants(pageOneEntrants_JSON);
                for (int page = 2; page <= totalPages_entrants; page++) {
                    String perPage_JSON = httpRequest.makeStartGgRequest(slug, RequestType.ENTRANTS, page, ENTRANTS_PERPAGECOUNT);
                    entrants.addAll(parseEntrants(perPage_JSON));
                }


                // Build a way to lookup entrantId -> globalUserId
                Map<Integer, Integer> entrantToGlobal = new HashMap<>();
                List<Player> players = new ArrayList<>();
                for (Entrant e : entrants) {
                    entrantToGlobal.put(e.getEntrantId(), e.getGlobalId());
                    players.add(new Player(e.getName(), e.getGlobalId()));
                }

                // Now pass the map into every parseMatches call
                List<ImportedMatch> matches = new ArrayList<>(parseMatches(pageOneSets_JSON, entrantToGlobal));
                for (int page = 2; page <= totalPages_sets; page++) {
                    String perPage_JSON = httpRequest.makeStartGgRequest(slug, RequestType.SETS, page, SETS_PERPAGECOUNT);
                    matches.addAll(parseMatches(perPage_JSON, entrantToGlobal));
                }

                eventImport.status_complete(matches, players, myEvent);
                repo.save(eventImport);
                eventImport.pullDomainEvents().forEach(eventPublisher::publishEvent);
                return "Import complete for "+myEvent.getTournamentName()+ ": "+ myEvent.getEventName()+"... has " + matches.size() + " matches among " + entrants.size()+ " entrants";

            } catch (Exception e) {
                //System.out.println("=== Import failed: " + e.getMessage());
                eventImport.status_fail(e.getMessage());
                repo.save(eventImport);
                eventImport.pullDomainEvents().forEach(eventPublisher::publishEvent);
                return "Import failed: " + e.getMessage();
            }
        }

        private int parseTotalPages(String json, String collectionName) throws Exception {
            return mapper.readTree(json)
                .path("data")
                .path("event")
                .path(collectionName)
                .path("pageInfo")
                .path("totalPages")
                .asInt();
        }

        private StartGgEvent parseEvent(String json) throws Exception{
            JsonNode root = mapper.readTree(json);
            JsonNode event = root.path("data").path("event");
            JsonNode tournament = event.path("tournament");

            return new StartGgEvent(
                tournament.path("name").asText(),
                tournament.path("id").asInt(),
                event.path("name").asText(),
                event.path("id").asInt(),
                event.path("startAt").asLong()
            );
        }
        public List<Entrant> parseEntrants(String json) throws Exception{
            JsonNode nodes = mapper.readTree(json)
            .path("data")
            .path("event")
            .path("entrants")
            .path("nodes");

            List<Entrant> entrants = new ArrayList<>();
            for (JsonNode node : nodes) {
                String name = node.path("name").asText();
                int entrantId = node.path("id").asInt();
                int globalUserId = node.path("participants")
                    .path(0)
                    .path("user")
                    .path("id")
                    .asInt();

                entrants.add(new Entrant(name, entrantId, globalUserId));
            }
            return entrants;
        }
        public List<ImportedMatch> parseMatches(String json, Map<Integer, Integer> entrantToGlobal) throws Exception {
            JsonNode nodes = mapper.readTree(json)
                .path("data")
                .path("event")
                .path("sets")
                .path("nodes");

            List<ImportedMatch> matches = new ArrayList<>();
            for (JsonNode node : nodes) {
                int winnerEntrantId = node.path("winnerId").asInt();

                JsonNode slots = node.path("slots");
                int loserEntrantId = -1;
                for (JsonNode slot : slots) {
                    int entrantId = slot.path("entrant").path("id").asInt();
                    if (entrantId != winnerEntrantId) {
                        loserEntrantId = entrantId;
                        break;
                    }
                }

                if (loserEntrantId == -1) continue;

                int winnerId = entrantToGlobal.getOrDefault(winnerEntrantId, -1);
                int loserId = entrantToGlobal.getOrDefault(loserEntrantId, -1);

                if (winnerId == -1 || loserId == -1) continue; // skip if entrant not found

                matches.add(new ImportedMatch(winnerId, loserId));
            }
            return matches;
        }
    }
