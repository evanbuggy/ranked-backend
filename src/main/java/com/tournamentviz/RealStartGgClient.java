package com.tournamentviz;

import com.shared.RabbitMqConfiguration;
import com.shared.exceptions.MyApplicationExceptions;
import com.shared.exceptions.MyApplicationExceptions.BadRequestException;
import com.startGgIntegration.StartGgApiHandler;
import com.startGgIntegration.systemEvents.EventImported;
import com.startGgIntegration.systemEvents.ImportFailure;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(name = "app.startgg.mode", havingValue = "real")
public class RealStartGgClient implements StartggClient {

    private final StartGgApiHandler myHandler;
    private final AtomicReference<CompletableFuture<StartggTournamentData>> pendingImport = new AtomicReference<>();

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

        CompletableFuture<StartggTournamentData> future = pendingImport.get();
        if (future != null) {
            future.complete(new StartggTournamentData(players, matches));
        }
    }

    @RabbitListener(queues = RabbitMqConfiguration.IMPORT_FAILURE_QUEUE)  // add this listener
    public void onImportFailure(ImportFailure event) {
        CompletableFuture<StartggTournamentData> future = pendingImport.get();
        if (future != null) {
            future.completeExceptionally(new BadRequestException(event.reason()));
        }
    }

    @Override
    public StartggTournamentData fetchTournament(String tournamentRef) {
        CompletableFuture<StartggTournamentData> future = new CompletableFuture<>();
        pendingImport.set(future);
        myHandler.importEvent(tournamentRef, 1);
        try {
            return future.get(30, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof MyApplicationExceptions.AppException ex) throw ex;
            throw new MyApplicationExceptions.BadRequestException("Import failed: " + cause.getMessage());
        } catch (TimeoutException e) {
            throw new MyApplicationExceptions.BadRequestException("Start.gg import timed out");
        } catch (Exception e) {
            throw new MyApplicationExceptions.BadRequestException("Unexpected import error: " + e.getMessage());
        } finally {
            pendingImport.set(null);
        }
    }
}