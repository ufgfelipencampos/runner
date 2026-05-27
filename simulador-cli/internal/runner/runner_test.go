package runner

import (
	"fmt"
	"net"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"testing"
)

// --- IsPortInUse ---

func TestIsPortInUseWhenListening(t *testing.T) {
	listener, err := net.Listen("tcp", "localhost:0")
	if err != nil {
		t.Fatalf("failed to create listener: %v", err)
	}
	defer listener.Close()

	port := listener.Addr().(*net.TCPAddr).Port
	if !IsPortInUse(port) {
		t.Fatalf("expected port %d to be reported as in use", port)
	}
}

func TestIsPortInUseWhenFree(t *testing.T) {
	if IsPortInUse(59980) {
		t.Fatalf("expected port 59980 to be reported as free")
	}
}

// --- StartServer ---

func TestStartServerReturnsStartupJSON(t *testing.T) {
	tempDir := t.TempDir()
	javaPath, _ := createFakeJava(t)
	jarPath := filepath.Join(tempDir, "simulador.jar")
	if err := os.WriteFile(jarPath, []byte("fake-jar"), 0o644); err != nil {
		t.Fatalf("failed to create fake jar: %v", err)
	}

	t.Setenv("FAKE_JAVA_BEHAVIOR", "start_success")

	result, err := Config{JavaBin: javaPath, JarPath: jarPath}.
		StartServer([]string{"server", "start", "--port", "8443"})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if result.ExitCode != 0 {
		t.Fatalf("expected successful startup, got exit code %d", result.ExitCode)
	}
	if !strings.Contains(result.Stdout, "\"operation\": \"server-start\"") {
		t.Fatalf("unexpected startup output: %s", result.Stdout)
	}
	if result.PID == 0 {
		t.Fatalf("expected a child process pid for the background server")
	}
}

func TestStartServerReturnsErrorWhenJarFails(t *testing.T) {
	tempDir := t.TempDir()
	javaPath, _ := createFakeJava(t)
	jarPath := filepath.Join(tempDir, "simulador.jar")
	if err := os.WriteFile(jarPath, []byte("fake-jar"), 0o644); err != nil {
		t.Fatalf("failed to create fake jar: %v", err)
	}

	t.Setenv("FAKE_JAVA_BEHAVIOR", "start_error")

	result, err := Config{JavaBin: javaPath, JarPath: jarPath}.
		StartServer([]string{"server", "start", "--port", "8443"})
	if err != nil {
		t.Fatalf("unexpected hard error: %v", err)
	}
	if result.ExitCode == 0 {
		t.Fatalf("expected non-zero exit code on jar failure")
	}
}

// --- validate ---

func TestValidateReturnsHelpfulErrorWhenJavaIsMissing(t *testing.T) {
	tempDir := t.TempDir()
	jarPath := filepath.Join(tempDir, "simulador.jar")
	if err := os.WriteFile(jarPath, []byte("fake-jar"), 0o644); err != nil {
		t.Fatalf("failed to create fake jar: %v", err)
	}

	_, err := Config{
		JavaBin: filepath.Join(tempDir, "missing-java"),
		JarPath: jarPath,
	}.StartServer([]string{"server", "start", "--port", "8443"})
	if err == nil {
		t.Fatalf("expected error when java is missing")
	}
	if !strings.Contains(err.Error(), "Java nao encontrado") {
		t.Fatalf("unexpected error: %v", err)
	}
}

func TestValidateReturnsHelpfulErrorWhenJarIsMissing(t *testing.T) {
	tempDir := t.TempDir()
	javaPath, _ := createFakeJava(t)

	_, err := Config{
		JavaBin: javaPath,
		JarPath: filepath.Join(tempDir, "missing.jar"),
	}.StartServer([]string{"server", "start", "--port", "8443"})
	if err == nil {
		t.Fatalf("expected error when jar is missing")
	}
	if !strings.Contains(err.Error(), "simulador.jar nao encontrado") {
		t.Fatalf("unexpected error: %v", err)
	}
}

// --- Stop ---

func TestStopSendsPostToShutdown(t *testing.T) {
	called := false
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method == http.MethodPost && r.URL.Path == "/shutdown" {
			called = true
			fmt.Fprint(w, `{"status":"SUCCESS","message":"Simulador encerrado."}`)
		}
	}))
	defer srv.Close()

	port := srv.Listener.Addr().(*net.TCPAddr).Port

	result, err := Stop(port)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if !called {
		t.Fatalf("expected POST /shutdown to be called")
	}
	if !strings.Contains(result.Stdout, "SUCCESS") {
		t.Fatalf("unexpected result: %s", result.Stdout)
	}
}

func TestStopReturnsErrorWhenSimuladorNotRunning(t *testing.T) {
	_, err := Stop(59990)
	if err == nil {
		t.Fatalf("expected error when simulador is not running")
	}
	if !strings.Contains(err.Error(), "porta 59990") {
		t.Fatalf("unexpected error: %v", err)
	}
}

// --- Status ---

func TestStatusReturnsOnlineWhenRunning(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path == "/api/info" {
			fmt.Fprint(w, `{"status":"ONLINE","version":"1.0.0"}`)
		}
	}))
	defer srv.Close()

	port := srv.Listener.Addr().(*net.TCPAddr).Port

	result, err := Status(port)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if !strings.Contains(result.Stdout, "ONLINE") {
		t.Fatalf("unexpected result: %s", result.Stdout)
	}
}

func TestStatusReturnsRunningWhenEndpointNotFound(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusNotFound)
		fmt.Fprint(w, "<h1>404 Not Found</h1>")
	}))
	defer srv.Close()

	port := srv.Listener.Addr().(*net.TCPAddr).Port

	result, err := Status(port)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if !strings.Contains(result.Stdout, "RUNNING") {
		t.Fatalf("expected RUNNING when server is up but endpoint missing, got: %s", result.Stdout)
	}
	if !strings.Contains(result.Stdout, "404") {
		t.Fatalf("expected status code 404 in message, got: %s", result.Stdout)
	}
}

func TestStatusReturnsOfflineWhenNotRunning(t *testing.T) {
	result, err := Status(59991)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if !strings.Contains(result.Stdout, "OFFLINE") {
		t.Fatalf("expected OFFLINE in result, got: %s", result.Stdout)
	}
	if !strings.Contains(result.Stdout, "59991") {
		t.Fatalf("expected port in result, got: %s", result.Stdout)
	}
}

// --- validate (caminho é diretório) ---

func TestValidateReturnsErrorWhenJarIsDirectory(t *testing.T) {
	tempDir := t.TempDir()
	javaPath, _ := createFakeJava(t)

	_, err := Config{
		JavaBin: javaPath,
		JarPath: tempDir, // diretório, não arquivo
	}.StartServer([]string{"server", "start", "--port", "8443"})
	if err == nil {
		t.Fatalf("expected error when jar path is a directory")
	}
	if !strings.Contains(err.Error(), "diretorio") {
		t.Fatalf("unexpected error message: %v", err)
	}
}

// --- jarArgs ---

func TestJarArgsContainsJarFlagAndPath(t *testing.T) {
	c := Config{JavaBin: "java", JarPath: "/caminho/simulador.jar"}
	args := c.jarArgs([]string{"server", "start", "--port", "8443"})

	if len(args) < 4 {
		t.Fatalf("expected at least 4 args, got %d: %v", len(args), args)
	}
	if args[0] != "-jar" {
		t.Fatalf("expected args[0]==-jar, got %q", args[0])
	}
	if args[1] != "/caminho/simulador.jar" {
		t.Fatalf("expected args[1]==jar path, got %q", args[1])
	}
	if args[2] != "server" || args[3] != "start" {
		t.Fatalf("expected command args after jar path, got %v", args[2:])
	}
}

// --- readFirstJSONObject ---

func TestReadFirstJSONObjectParsesSimpleObject(t *testing.T) {
	input := `{"status":"SUCCESS","operation":"server-start"}`
	result, err := readFirstJSONObject(strings.NewReader(input))
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if result != input {
		t.Fatalf("expected %q, got %q", input, result)
	}
}

func TestReadFirstJSONObjectSkipsLeadingWhitespace(t *testing.T) {
	input := "   \n\t{\"status\":\"SUCCESS\"}"
	result, err := readFirstJSONObject(strings.NewReader(input))
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if !strings.Contains(result, `"status"`) {
		t.Fatalf("expected parsed JSON, got: %q", result)
	}
}

func TestReadFirstJSONObjectHandlesNestedObjects(t *testing.T) {
	input := `{"outer":{"inner":"value"},"x":1}`
	result, err := readFirstJSONObject(strings.NewReader(input))
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if !strings.Contains(result, "inner") {
		t.Fatalf("expected nested key in result, got: %q", result)
	}
	if result != input {
		t.Fatalf("expected complete object, got: %q", result)
	}
}

func TestReadFirstJSONObjectReturnsErrorOnEmptyInput(t *testing.T) {
	_, err := readFirstJSONObject(strings.NewReader(""))
	if err == nil {
		t.Fatalf("expected error on empty input")
	}
}

// --- helpers ---

func createFakeJava(t *testing.T) (string, string) {
	t.Helper()

	tempDir := t.TempDir()
	logPath := filepath.Join(tempDir, "fake-java.log")

	if runtime.GOOS == "windows" {
		javaPath := filepath.Join(tempDir, "java.cmd")
		script := "@echo off\r\n" +
			"if not \"%FAKE_JAVA_LOG%\"==\"\" echo %*>>\"%FAKE_JAVA_LOG%\"\r\n" +
			"if \"%FAKE_JAVA_BEHAVIOR%\"==\"start_success\" (\r\n" +
			"  echo {\r\n" +
			"  echo   \"status\": \"SUCCESS\",\r\n" +
			"  echo   \"operation\": \"server-start\",\r\n" +
			"  echo   \"port\": 8443\r\n" +
			"  echo }\r\n" +
			"  powershell -NoProfile -Command \"Start-Sleep -Seconds 3\" >NUL\r\n" +
			"  exit /b 0\r\n" +
			")\r\n" +
			"if \"%FAKE_JAVA_BEHAVIOR%\"==\"start_error\" (\r\n" +
			"  >&2 echo {\"status\":\"ERROR\",\"type\":\"RUNTIME_ERROR\"}\r\n" +
			"  exit /b 1\r\n" +
			")\r\n" +
			"echo {\"status\":\"SUCCESS\"}\r\n"
		if err := os.WriteFile(javaPath, []byte(script), 0o755); err != nil {
			t.Fatalf("failed to write fake java script: %v", err)
		}
		return javaPath, logPath
	}

	javaPath := filepath.Join(tempDir, "java")
	script := "#!/bin/sh\n" +
		"if [ -n \"$FAKE_JAVA_LOG\" ]; then printf '%s\\n' \"$*\" >> \"$FAKE_JAVA_LOG\"; fi\n" +
		"case \"$FAKE_JAVA_BEHAVIOR\" in\n" +
		"  start_success)\n" +
		"    cat <<'EOF'\n" +
		"{\n" +
		"  \"status\": \"SUCCESS\",\n" +
		"  \"operation\": \"server-start\",\n" +
		"  \"port\": 8443\n" +
		"}\n" +
		"EOF\n" +
		"    sleep 3\n" +
		"    exit 0\n" +
		"    ;;\n" +
		"  start_error)\n" +
		"    printf '{\"status\":\"ERROR\",\"type\":\"RUNTIME_ERROR\"}\\n' >&2\n" +
		"    exit 1\n" +
		"    ;;\n" +
		"esac\n" +
		"printf '{\"status\":\"SUCCESS\"}\\n'\n"
	if err := os.WriteFile(javaPath, []byte(script), 0o755); err != nil {
		t.Fatalf("failed to write fake java script: %v", err)
	}
	return javaPath, logPath
}
