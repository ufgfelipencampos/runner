package br.ufg.runner.assinador;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

final class CliArguments {
    private static final Pattern ALIAS_PATTERN = Pattern.compile("[A-Za-z0-9_-]{3,64}");
    private static final Pattern SLOT_PATTERN = Pattern.compile("\\d+");

    private final CommandType commandType;
    private final Path inputPath;
    private final Path outputPath;
    private final OperationMode mode;
    private final String alias;
    private final String pkcs11Library;
    private final String pkcs11Slot;

    private CliArguments(
        CommandType commandType,
        Path inputPath,
        Path outputPath,
        OperationMode mode,
        String alias,
        String pkcs11Library,
        String pkcs11Slot
    ) {
        this.commandType = commandType;
        this.inputPath = inputPath;
        this.outputPath = outputPath;
        this.mode = mode;
        this.alias = alias;
        this.pkcs11Library = pkcs11Library;
        this.pkcs11Slot = pkcs11Slot;
    }

    static CliArguments parse(String[] args) throws ValidationException {
        if (args.length == 0) {
            throw new ValidationException("Nenhum comando informado.\n" + usage());
        }

        CommandType commandType = CommandType.fromCliName(args[0]);
        Map<String, String> flags = parseFlags(args);

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

        return new CliArguments(commandType, inputPath, outputPath, mode, alias, pkcs11Library, pkcs11Slot);
    }

    private static Map<String, String> parseFlags(String[] args) throws ValidationException {
        Map<String, String> flags = new HashMap<>();

        for (int index = 1; index < args.length; index++) {
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
            || "--pkcs11-slot".equals(flag);
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

    static String usage() {
        return """
            Uso:
              sign --pathin <arquivo.json> --pathout <saida.json> --mode one-time --alias <nome> [--pkcs11-lib <lib>] [--pkcs11-slot <slot>]
              validate --pathin <arquivo.json> --pathout <saida.json> --mode one-time
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
}

