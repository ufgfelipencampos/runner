package br.ufg.runner.assinador;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.ServerSocket;

public final class AssinadorApplicationTest {
    public static void main(String[] args) throws Exception {
        AssinadorApplicationTest testSuite = new AssinadorApplicationTest();
        testSuite.shouldSignAndValidateInOneTimeMode();
        testSuite.shouldStartStatusAndStopServer();
        testSuite.shouldUseDefaultPortForServerStart();
        testSuite.shouldRejectPayloadWithoutResourceType();
        testSuite.shouldRejectMissingAliasForSign();
        testSuite.shouldRejectUnsupportedMode();
        testSuite.shouldRejectInvalidPkcs11LibraryExtension();
        testSuite.shouldRejectSameInputAndOutputPath();
        testSuite.shouldRejectSigningFlagsOnServerCommand();
        testSuite.shouldReportInvalidSignatureAsFalse();
        testSuite.shouldRejectServerStartWhenPortIsAlreadyInUse();
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

    private void shouldStartStatusAndStopServer() throws Exception {
        int port = findFreePort();

        InvocationResult startResult = run(
            "server",
            "start",
            "--port", Integer.toString(port)
        );

        try {
            assertEquals(ExitCode.SERVER_RUNNING.value(), startResult.exitCode(), "server start should keep the process alive");
            assertContains(startResult.stdout(), "\"operation\": \"server-start\"", "server start should report startup data");

            InvocationResult statusResult = run(
                "server",
                "status",
                "--port", Integer.toString(port)
            );

            assertEquals(0, statusResult.exitCode(), "server status should succeed while running");
            assertContains(statusResult.stdout(), "\"running\": true", "server status should report the service as running");

            InvocationResult stopResult = run(
                "server",
                "stop",
                "--port", Integer.toString(port)
            );

            assertEquals(0, stopResult.exitCode(), "server stop should succeed");
            assertContains(stopResult.stdout(), "\"server-stop\"", "server stop should confirm shutdown");

            waitForServerToStop(port);

            InvocationResult stoppedStatus = run(
                "server",
                "status",
                "--port", Integer.toString(port)
            );

            assertEquals(0, stoppedStatus.exitCode(), "server status should still succeed after stop");
            assertContains(stoppedStatus.stdout(), "\"running\": false", "server status should report the service as stopped");
        } finally {
            try {
                run("server", "stop", "--port", Integer.toString(port));
            } catch (Exception ignored) {
                // Best effort cleanup in case the test fails before the explicit stop.
            }
        }
    }

    private void shouldUseDefaultPortForServerStart() throws Exception {
        CliArguments arguments = CliArguments.parse(new String[] {"server", "start"});
        assertEquals(CommandType.SERVER, arguments.commandType(), "server start should parse as server command");
        assertEquals(ServerCommandType.START, arguments.serverCommandType(), "server start should parse the start action");
        assertEquals(8080, arguments.serverPort(), "server start should default to port 8080");
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

    private void shouldRejectMissingAliasForSign() throws Exception {
        Path tempDir = Files.createTempDirectory("assinador-test-missing-alias");
        Path inputPath = createValidInput(tempDir.resolve("entrada.json"));
        Path outputPath = tempDir.resolve("saida.json");

        InvocationResult result = run(
            "sign",
            "--pathin", inputPath.toString(),
            "--pathout", outputPath.toString(),
            "--mode", "one-time"
        );

        assertEquals(ExitCode.VALIDATION_ERROR.value(), result.exitCode(), "sign should require alias");
        assertContains(result.stderr(), "--alias", "stderr should explain that alias is required");
    }

    private void shouldRejectUnsupportedMode() throws Exception {
        Path tempDir = Files.createTempDirectory("assinador-test-mode");
        Path inputPath = createValidInput(tempDir.resolve("entrada.json"));
        Path outputPath = tempDir.resolve("saida.json");

        InvocationResult result = run(
            "validate",
            "--pathin", inputPath.toString(),
            "--pathout", outputPath.toString(),
            "--mode", "server"
        );

        assertEquals(ExitCode.VALIDATION_ERROR.value(), result.exitCode(), "unsupported modes should be rejected");
        assertContains(result.stderr(), "one-time", "stderr should point the user to the supported mode");
    }

    private void shouldRejectInvalidPkcs11LibraryExtension() throws Exception {
        Path tempDir = Files.createTempDirectory("assinador-test-pkcs11");
        Path inputPath = createValidInput(tempDir.resolve("entrada.json"));
        Path outputPath = tempDir.resolve("saida.json");

        InvocationResult result = run(
            "sign",
            "--pathin", inputPath.toString(),
            "--pathout", outputPath.toString(),
            "--mode", "one-time",
            "--alias", "test-signer",
            "--pkcs11-lib", "token.txt"
        );

        assertEquals(ExitCode.VALIDATION_ERROR.value(), result.exitCode(), "invalid PKCS#11 library names should be rejected");
        assertContains(result.stderr(), ".dll, .so ou .dylib", "stderr should list supported PKCS#11 extensions");
    }

    private void shouldRejectSameInputAndOutputPath() throws Exception {
        Path tempDir = Files.createTempDirectory("assinador-test-same-path");
        Path inputPath = createValidInput(tempDir.resolve("entrada.json"));

        InvocationResult result = run(
            "validate",
            "--pathin", inputPath.toString(),
            "--pathout", inputPath.toString(),
            "--mode", "one-time"
        );

        assertEquals(ExitCode.VALIDATION_ERROR.value(), result.exitCode(), "input and output should not be the same file");
        assertContains(result.stderr(), "entrada e saida", "stderr should explain the conflicting paths");
    }

    private void shouldRejectSigningFlagsOnServerCommand() throws Exception {
        InvocationResult result = run(
            "server",
            "status",
            "--alias", "test-signer"
        );

        assertEquals(ExitCode.VALIDATION_ERROR.value(), result.exitCode(), "server should reject signing flags");
        assertContains(result.stderr(), "nao aceita flags de assinatura/verificacao", "stderr should explain the invalid flag set");
    }

    private void shouldReportInvalidSignatureAsFalse() throws Exception {
        Path tempDir = Files.createTempDirectory("assinador-test-invalid-signature");
        Path inputPath = tempDir.resolve("assinado-invalido.json");
        Path outputPath = tempDir.resolve("resultado.json");

        Files.writeString(
            inputPath,
            """
                {
                  "status": "SUCCESS",
                  "operation": "sign",
                  "mode": "one-time",
                  "inputDigestSha256": "ABC123",
                  "signature": "SIMULATED-SIGNATURE-OTHER",
                  "signedBy": "test-signer"
                }
                """,
            StandardCharsets.UTF_8
        );

        InvocationResult result = run(
            "validate",
            "--pathin", inputPath.toString(),
            "--pathout", outputPath.toString(),
            "--mode", "one-time"
        );

        assertEquals(ExitCode.SUCCESS.value(), result.exitCode(), "validate should complete even for invalid simulated signatures");
        assertContains(result.stdout(), "\"valid\": false", "validate should report invalid signatures as false");
        assertContains(result.stdout(), "\"reason\":", "validate should explain the invalid result");
        assertContains(Files.readString(outputPath), "\"valid\": false", "validate output file should persist the invalid result");
    }

    private void shouldRejectServerStartWhenPortIsAlreadyInUse() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            int port = socket.getLocalPort();

            InvocationResult result = run(
                "server",
                "start",
                "--port", Integer.toString(port)
            );

            assertEquals(ExitCode.VALIDATION_ERROR.value(), result.exitCode(), "server start should fail on occupied ports");
            assertContains(result.stderr(), "porta " + port, "stderr should mention the conflicting port");
            assertContains(result.stderr(), "Tente outra porta", "stderr should suggest a next action");
        }
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

    private static Path createValidInput(Path inputPath) throws Exception {
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
        return inputPath;
    }

    private static int findFreePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        }
    }

    private static void waitForServerToStop(int port) throws Exception {
        for (int attempt = 0; attempt < 20; attempt++) {
            InvocationResult statusResult = new AssinadorApplicationTest().run(
                "server",
                "status",
                "--port", Integer.toString(port)
            );

            if (statusResult.stdout().contains("\"running\": false")) {
                return;
            }

            Thread.sleep(50L);
        }

        throw new AssertionError("server did not stop in time");
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + " (expected " + expected + ", got " + actual + ")");
        }
    }

    private static void assertEquals(CommandType expected, CommandType actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + " (expected " + expected + ", got " + actual + ")");
        }
    }

    private static void assertEquals(ServerCommandType expected, ServerCommandType actual, String message) {
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
