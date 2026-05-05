package br.ufg.runner.assinador;

import java.io.IOException;
import java.net.BindException;
import java.net.ServerSocket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class SimulatedServerService {
    private static final Map<Integer, ServerHandle> RUNNING_SERVERS = new ConcurrentHashMap<>();

    private SimulatedServerService() {
    }

    static String start(int port) throws ValidationException {
        if (RUNNING_SERVERS.containsKey(port)) {
            throw new ValidationException("Ja existe um servidor em execucao na porta " + port + ". Use server status ou escolha outra porta.");
        }

        try {
            ServerSocket socket = new ServerSocket(port);
            socket.setReuseAddress(true);
            RUNNING_SERVERS.put(port, new ServerHandle(port, socket));
            return serverResponse(
                "server-start",
                "SUCCESS",
                "Servidor simulado iniciado com sucesso.",
                port,
                true
            );
        } catch (BindException error) {
            throw new ValidationException("Nao foi possivel iniciar o servidor na porta " + port + " porque ela esta em uso. Tente outra porta.");
        } catch (IOException error) {
            throw new ValidationException("Nao foi possivel iniciar o servidor na porta " + port + ": " + error.getMessage());
        }
    }

    static String status(Integer port) {
        if (port == null) {
            boolean running = !RUNNING_SERVERS.isEmpty();
            String message = running
                ? "Existe ao menos um servidor simulado em execucao."
                : "Nenhum servidor simulado esta em execucao.";
            return """
                {
                  "status": "SUCCESS",
                  "operation": "server-status",
                  "message": "%s",
                  "running": %s,
                  "activeServers": %s
                }
                """.formatted(
                JsonEscaper.escape(message),
                Boolean.toString(running),
                Integer.toString(RUNNING_SERVERS.size())
            );
        }

        boolean running = RUNNING_SERVERS.containsKey(port);
        String message = running
            ? "Servidor simulado ativo na porta informada."
            : "Nao existe servidor simulado ativo na porta informada.";
        return serverResponse("server-status", "SUCCESS", message, port, running);
    }

    static String stop(Integer port) throws ValidationException {
        if (port == null) {
            throw new ValidationException("O comando server stop exige a flag --port para identificar qual servidor deve ser encerrado.");
        }

        ServerHandle handle = RUNNING_SERVERS.remove(port);
        if (handle == null) {
            return serverResponse(
                "server-stop",
                "SUCCESS",
                "Nenhum servidor simulado estava ativo na porta informada.",
                port,
                false
            );
        }

        try {
            handle.socket().close();
        } catch (IOException error) {
            throw new ValidationException("Falha ao encerrar o servidor na porta " + port + ": " + error.getMessage());
        }

        return serverResponse(
            "server-stop",
            "SUCCESS",
            "Servidor simulado encerrado com sucesso.",
            port,
            false
        );
    }

    private static String serverResponse(String operation, String status, String message, int port, boolean running) {
        return """
            {
              "status": "%s",
              "operation": "%s",
              "message": "%s",
              "port": %s,
              "endpoint": "http://127.0.0.1:%s",
              "running": %s
            }
            """.formatted(
            JsonEscaper.escape(status),
            JsonEscaper.escape(operation),
            JsonEscaper.escape(message),
            Integer.toString(port),
            Integer.toString(port),
            Boolean.toString(running)
        );
    }

    private record ServerHandle(int port, ServerSocket socket) {
    }
}
