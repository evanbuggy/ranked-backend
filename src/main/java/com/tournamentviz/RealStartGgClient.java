package com.tournamentviz;

import com.shared.RabbitMqConfiguration;
import com.startGgIntegration.StartGgApiHandler;
import com.startGgIntegration.systemEvents.EventImported;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(name = "app.startgg.mode", havingValue = "real")
public class RealStartGgClient implements StartggClient {

    private final StartGgApiHandler myHandler;
    private CompletableFuture<StartggTournamentData> pendingImport;

    public RealStartGgClient(StartGgApiHandler handler) {
        this.myHandler = handler;
    }

    @RabbitListener(queues = RabbitMqConfiguration.EVENT_IMPORTED_QUEUE)
    public void onEventImported(EventImported event) {
        List<StartggPlayer> players = event.players().stream()
            .map(p -> new StartggPlayer(String.valueOf(p.getGlobalStartGgId()), p.getTag()))
            .collect(Collectors.toList());

        List<StartggMatch> matches = new ArrayList<>();
        for (int i = 0; i < event.matches().size(); i++) {
            var m = event.matches().get(i);
            matches.add(new StartggMatch(
                "m" + i, i,
                String.valueOf(m.getWinnerId()),
                String.valueOf(m.getLoserId()),
                String.valueOf(m.getWinnerId()),
                Instant.now()
            ));
        }

        StartggTournamentData data = new StartggTournamentData(players, matches);

        // complete the future so fetchTournament() can return
        if (pendingImport != null) {
            pendingImport.complete(data);
        }
    }

    @Override
    public StartggTournamentData fetchTournament(String tournamentRef) {
        pendingImport = new CompletableFuture<>();
        myHandler.importEvent(tournamentRef, 1);
        try {
            // wait up to 30 seconds for RabbitMQ to deliver the event
            return pendingImport.get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            return new StartggTournamentData(new ArrayList<>(), new ArrayList<>());
        } finally {
            pendingImport = null;
        }
    }
}