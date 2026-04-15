package br.ufg.runner.assinador;

import java.net.ServerSocket;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

final class SimulatedServerService {
    private static final ConcurrentHashMap<Integer, ServerEntry> RUNNING_SERVERS = new ConcurrentHashMap<>();

    private SimulatedServerService() {
    }

    static String start(int port) throws Exception {
        if (RUNNING_SERVERS.containsKey(port)) {
            throw new ValidationException("Ja existe um servidor simulado rodando na porta " + port + ".");
        }

        ServerSocket serverSocket = new ServerSocket(port);
        String startedAt = Instant.now().toString();

        Thread acceptThread = new Thread(() -> acceptLoop(serverSocket));
        acceptThread.setDaemon(true);
        acceptThread.start();

        RUNNING_SERVERS.put(port, new ServerEntry(serverSocket, startedAt));

        return """
            {
              "status": "SUCCESS",
              "operation": "server-start",
              "port": %d,
              "message": "Servidor simulado iniciado.",
              "startedAt": "%s"
            }
            """.formatted(port, JsonEscaper.escape(startedAt));
    }

    static String status(int port) {
        ServerEntry entry = RUNNING_SERVERS.get(port);
        boolean running = entry != null && !entry.socket().isClosed();
        String checkedAt = Instant.now().toString();

        return """
            {
              "status": "SUCCESS",
              "operation": "server-status",
              "port": %d,
              "running": %s,
              "checkedAt": "%s"
            }
            """.formatted(port, Boolean.toString(running), JsonEscaper.escape(checkedAt));
    }

    static String stop(int port) throws Exception {
        ServerEntry entry = RUNNING_SERVERS.remove(port);
        String stoppedAt = Instant.now().toString();

        if (entry != null && !entry.socket().isClosed()) {
            entry.socket().close();
        }

        return """
            {
              "status": "SUCCESS",
              "operation": "server-stop",
              "port": %d,
              "message": "Servidor simulado encerrado.",
              "stoppedAt": "%s"
            }
            """.formatted(port, JsonEscaper.escape(stoppedAt));
    }

    private static void acceptLoop(ServerSocket serverSocket) {
        while (!serverSocket.isClosed()) {
            try {
                serverSocket.accept().close();
            } catch (Exception ignored) {
                // Socket fechado ou interrompido — esperado durante o shutdown.
            }
        }
    }

    private record ServerEntry(ServerSocket socket, String startedAt) {
    }
}
