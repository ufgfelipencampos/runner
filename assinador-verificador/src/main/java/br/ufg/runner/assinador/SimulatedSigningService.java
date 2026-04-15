package br.ufg.runner.assinador;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SimulatedSigningService {
    private static final Pattern SIGNATURE_PATTERN = Pattern.compile("\"signature\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern DIGEST_PATTERN = Pattern.compile("\"inputDigestSha256\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern SIGNER_PATTERN = Pattern.compile("\"signedBy\"\\s*:\\s*\"([^\"]+)\"");

    String execute(CliArguments arguments) throws Exception {
        return switch (arguments.commandType()) {
            case SIGN -> sign(arguments);
            case VALIDATE -> validate(arguments);
            case SERVER -> executeServer(arguments);
        };
    }

    private String executeServer(CliArguments arguments) throws Exception {
        return switch (arguments.serverCommandType()) {
            case START -> SimulatedServerService.start(arguments.serverPort());
            case STATUS -> SimulatedServerService.status(arguments.serverPort());
            case STOP -> SimulatedServerService.stop(arguments.serverPort());
        };
    }

    private String sign(CliArguments arguments) throws Exception {
        String payload = Files.readString(arguments.inputPath(), StandardCharsets.UTF_8);
        validateSignPayload(payload);

        String digest = sha256(payload);
        String signature = "SIMULATED-SIGNATURE-" + digest;
        String library = arguments.pkcs11Library() == null ? "not-informed" : arguments.pkcs11Library();
        String slot = arguments.pkcs11Slot() == null ? "not-informed" : arguments.pkcs11Slot();
        String generatedAt = Instant.now().toString();

        return """
            {
              "status": "SUCCESS",
              "operation": "sign",
              "mode": "%s",
              "message": "Assinatura simulada gerada com sucesso.",
              "inputDigestSha256": "%s",
              "signature": "%s",
              "signedBy": "%s",
              "pkcs11Library": "%s",
              "pkcs11Slot": "%s",
              "generatedAt": "%s"
            }
            """.formatted(
            JsonEscaper.escape(arguments.mode().cliName()),
            JsonEscaper.escape(digest),
            JsonEscaper.escape(signature),
            JsonEscaper.escape(arguments.alias()),
            JsonEscaper.escape(library),
            JsonEscaper.escape(slot),
            JsonEscaper.escape(generatedAt)
        );
    }

    private String validate(CliArguments arguments) throws Exception {
        String signedPayload = Files.readString(arguments.inputPath(), StandardCharsets.UTF_8);
        String signature = requireField("signature", SIGNATURE_PATTERN, signedPayload);
        String digest = requireField("inputDigestSha256", DIGEST_PATTERN, signedPayload);
        String signer = optionalField(SIGNER_PATTERN, signedPayload, "unknown");
        String expectedSignature = "SIMULATED-SIGNATURE-" + digest;
        boolean valid = expectedSignature.equals(signature);
        String checkedAt = Instant.now().toString();
        String reason = valid
            ? "Assinatura simulada reconhecida e consistente com o digest."
            : "A assinatura simulada nao corresponde ao digest informado.";

        return """
            {
              "status": "SUCCESS",
              "operation": "validate",
              "mode": "%s",
              "message": "Validacao simulada concluida.",
              "valid": %s,
              "reason": "%s",
              "inputDigestSha256": "%s",
              "signature": "%s",
              "signedBy": "%s",
              "checkedAt": "%s"
            }
            """.formatted(
            JsonEscaper.escape(arguments.mode().cliName()),
            Boolean.toString(valid),
            JsonEscaper.escape(reason),
            JsonEscaper.escape(digest),
            JsonEscaper.escape(signature),
            JsonEscaper.escape(signer),
            JsonEscaper.escape(checkedAt)
        );
    }

    private void validateSignPayload(String payload) throws ValidationException {
        String normalized = payload.trim();
        if (normalized.isEmpty()) {
            throw new ValidationException("O conteudo do arquivo de entrada esta vazio.");
        }

        if (!(normalized.startsWith("{") && normalized.endsWith("}"))) {
            throw new ValidationException("O arquivo de entrada deve conter um objeto JSON.");
        }

        if (!normalized.contains("\"resourceType\"")) {
            throw new ValidationException("O JSON de entrada precisa conter o campo resourceType.");
        }
    }

    private String requireField(String fieldName, Pattern pattern, String input) throws ValidationException {
        Matcher matcher = pattern.matcher(input);
        if (!matcher.find()) {
            throw new ValidationException("O arquivo informado para validate nao contem o campo obrigatorio \"" + fieldName + "\".");
        }

        return matcher.group(1);
    }

    private String optionalField(Pattern pattern, String input, String fallback) {
        Matcher matcher = pattern.matcher(input);
        if (matcher.find()) {
            return matcher.group(1);
        }

        return fallback;
    }

    private String sha256(String payload) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] value = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().withUpperCase().formatHex(value);
    }
}

