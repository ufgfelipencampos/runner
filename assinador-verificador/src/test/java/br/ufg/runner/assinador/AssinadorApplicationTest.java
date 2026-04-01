package br.ufg.runner.assinador;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AssinadorApplicationTest {
    public static void main(String[] args) throws Exception {
        AssinadorApplicationTest testSuite = new AssinadorApplicationTest();
        testSuite.shouldSignAndValidateInOneTimeMode();
        testSuite.shouldRejectUnsupportedMode();
        testSuite.shouldRejectPayloadWithoutResourceType();
        System.out.println("All assinador-verificador tests passed.");
    }

    private void shouldSignAndValidateInOneTimeMode() throws Exception {
        Path tempDir = Files.createTempDirectory("assinador-test");
        Path inputPath = tempDir.resolve("entrada.json");
        Path signedPath = tempDir.resolve("assinado.json");
        Path validationPath = tempDir.resolve("validacao.json");

        Files.writeString(
            inputPath,
            """
                {
                  "resourceType": "Bundle",
                  "id": "bundle-001",
                  "entry": []
                }
                """,
            StandardCharsets.UTF_8
        );

        InvocationResult signResult = run(
            "sign",
            "--pathin", inputPath.toString(),
            "--pathout", signedPath.toString(),
            "--mode", "one-time",
            "--alias", "test-signer",
            "--pkcs11-lib", "token.dll",
            "--pkcs11-slot", "0"
        );

        assertEquals(0, signResult.exitCode(), "sign should exit with success");
        assertTrue(Files.exists(signedPath), "sign should create output file");
        assertContains(signResult.stdout(), "\"operation\": \"sign\"", "sign stdout should describe the operation");
        assertContains(Files.readString(signedPath), "\"signature\": \"SIMULATED-SIGNATURE-", "sign output should contain a simulated signature");

        InvocationResult validateResult = run(
            "validate",
            "--pathin", signedPath.toString(),
            "--pathout", validationPath.toString(),
            "--mode", "one-time"
        );

        assertEquals(0, validateResult.exitCode(), "validate should exit with success");
        assertTrue(Files.exists(validationPath), "validate should create output file");
        assertContains(validateResult.stdout(), "\"valid\": true", "validate stdout should report a valid signature");
    }

    private void shouldRejectUnsupportedMode() throws Exception {
        Path tempDir = Files.createTempDirectory("assinador-test-mode");
        Path inputPath = tempDir.resolve("entrada.json");
        Path outputPath = tempDir.resolve("saida.json");

        Files.writeString(inputPath, "{\"resourceType\":\"Bundle\"}", StandardCharsets.UTF_8);

        InvocationResult result = run(
            "sign",
            "--pathin", inputPath.toString(),
            "--pathout", outputPath.toString(),
            "--mode", "server",
            "--alias", "test-signer"
        );

        assertEquals(2, result.exitCode(), "server mode should be rejected for now");
        assertContains(result.stderr(), "Modo nao suportado", "stderr should explain that server mode is not implemented");
    }

    private void shouldRejectPayloadWithoutResourceType() throws Exception {
        Path tempDir = Files.createTempDirectory("assinador-test-payload");
        Path inputPath = tempDir.resolve("entrada.json");
        Path outputPath = tempDir.resolve("saida.json");

        Files.writeString(inputPath, "{\"id\":\"missing-resource-type\"}", StandardCharsets.UTF_8);

        InvocationResult result = run(
            "sign",
            "--pathin", inputPath.toString(),
            "--pathout", outputPath.toString(),
            "--mode", "one-time",
            "--alias", "test-signer"
        );

        assertEquals(2, result.exitCode(), "sign should reject non-FHIR-like payloads");
        assertContains(result.stderr(), "resourceType", "stderr should mention the missing field");
    }

    private InvocationResult run(String... args) {
        ByteArrayOutputStream stdoutBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream stderrBuffer = new ByteArrayOutputStream();
        AssinadorApplication application = new AssinadorApplication(
            new PrintStream(stdoutBuffer, true, StandardCharsets.UTF_8),
            new PrintStream(stderrBuffer, true, StandardCharsets.UTF_8),
            new SimulatedSigningService()
        );

        int exitCode = application.run(args);

        return new InvocationResult(
            exitCode,
            stdoutBuffer.toString(StandardCharsets.UTF_8),
            stderrBuffer.toString(StandardCharsets.UTF_8)
        );
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + " (expected " + expected + ", got " + actual + ")");
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertContains(String actual, String fragment, String message) {
        if (!actual.contains(fragment)) {
            throw new AssertionError(message + " (missing fragment: " + fragment + ")");
        }
    }

    private record InvocationResult(int exitCode, String stdout, String stderr) {
    }
}
