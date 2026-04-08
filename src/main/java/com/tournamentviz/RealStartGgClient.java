package com.tournamentviz;

import com.startGgIntegration.StartGgApiHandler;
import com.startGgIntegration.StartGgApiHandler.RequestType;
import com.startGgIntegration.entities.Entrant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(name = "app.startgg.mode", havingValue = "real")
public class RealStartGgClient implements StartggClient {

    private StartGgApiHandler myHandler;
    private StartggTournamentData lastImport = null;

    public RealStartGgClient(StartGgApiHandler handler) {
        System.out.println("Initializing RealStartggClient with StartGgApiHandler: " + handler);
        this.myHandler = handler;
    }

    @EventListener
    public void onEventImported(com.startGgIntegration.systemEvents.EventImported event) {
        System.out.println("=== EventImported received: " + event.players().size() + " players, " + event.matches().size() + " matches");

        List<StartggPlayer> players = event.players().stream()
            .map(p -> new StartggPlayer(String.valueOf(p.getGlobalStartGgId()), p.getTag()))
            .collect(Collectors.toList());

        List<StartggMatch> matches = new ArrayList<>();
        for (int i = 0; i < event.matches().size(); i++) {
            var m = event.matches().get(i);
            matches.add(new StartggMatch(
                "m" + i, i,
                String.valueOf(m.winnerId()),
                String.valueOf(m.loserId()),
                String.valueOf(m.winnerId()),
                Instant.now()
            ));
        }
        this.lastImport = new StartggTournamentData(players, matches);
    }

    @Override
    public StartggTournamentData fetchTournament(String tournamentRef) {
        lastImport = null;
        myHandler.importEvent(tournamentRef, 1); // publishes EventImported synchronously
        if (lastImport == null) {
            System.out.println("=== No EventImported event received — import likely failed");
            return new StartggTournamentData(new ArrayList<>(), new ArrayList<>());
        }
        return lastImport;
    }
}