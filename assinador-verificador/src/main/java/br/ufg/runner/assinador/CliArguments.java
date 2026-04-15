package br.ufg.runner.assinador;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

final class CliArguments {
    private static final Pattern ALIAS_PATTERN = Pattern.compile("[A-Za-z0-9_-]{3,64}");
    private static final Pattern SLOT_PATTERN = Pattern.compile("\\d+");
    private static final Pattern PORT_PATTERN = Pattern.compile("\\d+");
    private static final int DEFAULT_SERVER_PORT = 8080;

    private final CommandType commandType;
    private final Path inputPath;
    private final Path outputPath;
    private final OperationMode mode;
    private final String alias;
    private final String pkcs11Library;
    private final String pkcs11Slot;
    private final ServerCommandType serverCommandType;
    private final Integer serverPort;

    private CliArguments(
        CommandType commandType,
        Path inputPath,
        Path outputPath,
        OperationMode mode,
        String alias,
        String pkcs11Library,
        String pkcs11Slot,
        ServerCommandType serverCommandType,
        Integer serverPort
    ) {
        this.commandType = commandType;
        this.inputPath = inputPath;
        this.outputPath = outputPath;
        this.mode = mode;
        this.alias = alias;
        this.pkcs11Library = pkcs11Library;
        this.pkcs11Slot = pkcs11Slot;
        this.serverCommandType = serverCommandType;
        this.serverPort = serverPort;
    }

    static CliArguments parse(String[] args) throws ValidationException {
        if (args.length == 0) {
            throw new ValidationException("Nenhum comando informado.\n" + usage());
        }

        CommandType commandType = CommandType.fromCliName(args[0]);
        return switch (commandType) {
            case SIGN, VALIDATE -> parseSigningCommand(commandType, Arrays.copyOfRange(args, 1, args.length));
            case SERVER -> parseServerCommand(Arrays.copyOfRange(args, 1, args.length));
        };
    }

    private static CliArguments parseSigningCommand(CommandType commandType, String[] rawArgs) throws ValidationException {
        Map<String, String> flags = parseFlags(rawArgs);

        String rawPathIn = requireFlag(flags, "--pathin");
        String rawPathOut = requireFlag(flags, "--pathout");
        String rawMode = requireFlag(flags, "--mode");
        String alias = normalizeNullable(flags.get("--alias"));
        String pkcs11Library = normalizeNullable(flags.get("--pkcs11-lib"));
        String pkcs11Slot = normalizeNullable(flags.get("--pkcs11-slot"));

        Path inputPath = Path.of(rawPathIn).toAbsolutePath().normalize();
        Path outputPath = Path.of(rawPathOut).toAbsolutePath().normalize();
        OperationMode mode = OperationMode.fromCliName(rawMode);

        validatePaths(inputPath, outputPath);
        validateCommandSpecificFields(commandType, alias, pkcs11Library, pkcs11Slot);

        return new CliArguments(commandType, inputPath, outputPath, mode, alias, pkcs11Library, pkcs11Slot, null, null);
    }

    private static CliArguments parseServerCommand(String[] rawArgs) throws ValidationException {
        if (rawArgs.length == 0) {
            throw new ValidationException("O comando server exige uma acao: start, status ou stop.\n" + usage());
        }

        ServerCommandType serverCommandType = ServerCommandType.fromCliName(rawArgs[0]);
        Map<String, String> flags = parseFlags(Arrays.copyOfRange(rawArgs, 1, rawArgs.length));

        if (flags.containsKey("--pathin")
            || flags.containsKey("--pathout")
            || flags.containsKey("--mode")
            || flags.containsKey("--alias")
            || flags.containsKey("--pkcs11-lib")
            || flags.containsKey("--pkcs11-slot")) {
            throw new ValidationException("O comando server nao aceita flags de assinatura/verificacao.\n" + usage());
        }

        Integer serverPort = parseOptionalPort(flags.get("--port"));
        if (serverCommandType == ServerCommandType.START && serverPort == null) {
            serverPort = DEFAULT_SERVER_PORT;
        }

        return new CliArguments(CommandType.SERVER, null, null, null, null, null, null, serverCommandType, serverPort);
    }

    private static Map<String, String> parseFlags(String[] args) throws ValidationException {
        Map<String, String> flags = new HashMap<>();

        for (int index = 0; index < args.length; index++) {
            String flag = args[index];
            if (!flag.startsWith("--")) {
                throw new ValidationException("Argumento inesperado: " + flag + ".\n" + usage());
            }

            if (index + 1 >= args.length) {
                throw new ValidationException("A flag " + flag + " exige um valor.");
            }

            String value = args[++index];
            if (value.startsWith("--")) {
                throw new ValidationException("A flag " + flag + " exige um valor valido.");
            }

            if (flags.putIfAbsent(flag, value) != null) {
                throw new ValidationException("A flag " + flag + " foi informada mais de uma vez.");
            }
        }

        for (String flag : flags.keySet()) {
            if (!isSupportedFlag(flag)) {
                throw new ValidationException("Flag nao suportada: " + flag + ".\n" + usage());
            }
        }

        return flags;
    }

    private static boolean isSupportedFlag(String flag) {
        return "--pathin".equals(flag)
            || "--pathout".equals(flag)
            || "--mode".equals(flag)
            || "--alias".equals(flag)
            || "--pkcs11-lib".equals(flag)
            || "--pkcs11-slot".equals(flag)
            || "--port".equals(flag);
    }

    private static String requireFlag(Map<String, String> flags, String flag) throws ValidationException {
        String value = normalizeNullable(flags.get(flag));
        if (value == null) {
            throw new ValidationException("A flag obrigatoria " + flag + " nao foi informada.");
        }

        return value;
    }

    private static String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static void validatePaths(Path inputPath, Path outputPath) throws ValidationException {
        if (!Files.exists(inputPath)) {
            throw new ValidationException("Arquivo de entrada nao encontrado: " + inputPath);
        }

        if (!Files.isRegularFile(inputPath)) {
            throw new ValidationException("O caminho de entrada precisa apontar para um arquivo: " + inputPath);
        }

        if (!Files.isReadable(inputPath)) {
            throw new ValidationException("O arquivo de entrada nao pode ser lido: " + inputPath);
        }

        try {
            if (Files.size(inputPath) == 0L) {
                throw new ValidationException("O arquivo de entrada esta vazio: " + inputPath);
            }
        } catch (ValidationException error) {
            throw error;
        } catch (Exception error) {
            throw new ValidationException("Nao foi possivel verificar o tamanho do arquivo de entrada: " + inputPath);
        }

        if (!inputPath.getFileName().toString().toLowerCase().endsWith(".json")) {
            throw new ValidationException("O arquivo de entrada deve ter extensao .json.");
        }

        if (inputPath.equals(outputPath)) {
            throw new ValidationException("Os caminhos de entrada e saida nao podem ser o mesmo arquivo.");
        }

        Path outputParent = outputPath.getParent();
        if (outputParent != null && !Files.exists(outputParent)) {
            throw new ValidationException("O diretorio de saida nao existe: " + outputParent);
        }
    }

    private static void validateCommandSpecificFields(
        CommandType commandType,
        String alias,
        String pkcs11Library,
        String pkcs11Slot
    ) throws ValidationException {
        if (commandType == CommandType.SIGN) {
            if (alias == null) {
                throw new ValidationException("A flag obrigatoria --alias nao foi informada para o comando sign.");
            }

            if (!ALIAS_PATTERN.matcher(alias).matches()) {
                throw new ValidationException("O alias deve ter entre 3 e 64 caracteres e usar apenas letras, numeros, '-' ou '_'.");
            }
        }

        if (pkcs11Library != null) {
            String normalized = pkcs11Library.toLowerCase();
            if (!(normalized.endsWith(".dll") || normalized.endsWith(".so") || normalized.endsWith(".dylib"))) {
                throw new ValidationException("A biblioteca PKCS#11 deve terminar com .dll, .so ou .dylib.");
            }
        }

        if (pkcs11Slot != null && !SLOT_PATTERN.matcher(pkcs11Slot).matches()) {
            throw new ValidationException("O slot PKCS#11 deve ser um numero inteiro nao negativo.");
        }
    }

    private static Integer parseOptionalPort(String rawPort) throws ValidationException {
        String normalized = normalizeNullable(rawPort);
        if (normalized == null) {
            return null;
        }

        if (!PORT_PATTERN.matcher(normalized).matches()) {
            throw new ValidationException("A porta informada em --port deve ser numerica.");
        }

        int port = Integer.parseInt(normalized);
        if (port < 1 || port > 65535) {
            throw new ValidationException("A porta informada em --port deve estar entre 1 e 65535.");
        }

        return port;
    }

    static String usage() {
        return """
            Uso:
              sign --pathin <arquivo.json> --pathout <saida.json> --mode one-time --alias <nome> [--pkcs11-lib <lib>] [--pkcs11-slot <slot>]
              validate --pathin <arquivo.json> --pathout <saida.json> --mode one-time
              server start [--port <porta>]
              server status [--port <porta>]
              server stop [--port <porta>]
            """;
    }

    CommandType commandType() {
        return commandType;
    }

    Path inputPath() {
        return inputPath;
    }

    Path outputPath() {
        return outputPath;
    }

    OperationMode mode() {
        return mode;
    }

    String alias() {
        return alias;
    }

    String pkcs11Library() {
        return pkcs11Library;
    }

    String pkcs11Slot() {
        return pkcs11Slot;
    }

    ServerCommandType serverCommandType() {
        return serverCommandType;
    }

    Integer serverPort() {
        return serverPort;
    }
}

