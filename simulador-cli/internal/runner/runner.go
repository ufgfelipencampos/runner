package runner

import (
	"bufio"
	"bytes"
	"errors"
	"fmt"
	"io"
	"net/http"
	"os"
	"os/exec"
	"strings"
	"time"
)

type Config struct {
	JavaBin string
	JarPath string
}

type Result struct {
	Stdout   string
	Stderr   string
	ExitCode int
	PID      int
}

// Stop envia POST /shutdown ao simulador em execucao na porta indicada.
func Stop(port int) (Result, error) {
	url := fmt.Sprintf("http://localhost:%d/shutdown", port)

	client := &http.Client{Timeout: 10 * time.Second}
	resp, err := client.Post(url, "application/json", nil)
	if err != nil {
		return Result{}, fmt.Errorf(
			"Nao foi possivel conectar ao simulador na porta %d. Verifique se ele esta em execucao.", port,
		)
	}
	defer resp.Body.Close()

	body, _ := io.ReadAll(resp.Body)
	return Result{Stdout: string(body)}, nil
}

// Status consulta GET /api/info no simulador em execucao.
// Retorna JSON do servico se ativo, ou mensagem de OFFLINE se nao responder.
func Status(port int) (Result, error) {
	url := fmt.Sprintf("http://localhost:%d/api/info", port)

	client := &http.Client{Timeout: 5 * time.Second}
	resp, err := client.Get(url)
	if err != nil {
		offline := fmt.Sprintf(`{"status":"OFFLINE","port":%d,"message":"Simulador nao responde na porta %d."}`, port, port)
		return Result{Stdout: offline}, nil
	}
	defer resp.Body.Close()

	body, _ := io.ReadAll(resp.Body)
	return Result{Stdout: string(body)}, nil
}

// StartServer inicia o simulador.jar em segundo plano e aguarda o primeiro
// objeto JSON impresso no stdout como confirmacao de inicializacao.
func (c Config) StartServer(args []string) (Result, error) {
	if err := c.validate(); err != nil {
		return Result{}, err
	}

	command := exec.Command(c.JavaBin, c.jarArgs(args)...)
	stdoutPipe, err := command.StdoutPipe()
	if err != nil {
		return Result{}, fmt.Errorf("Nao foi possivel preparar a captura do stdout do simulador: %w", err)
	}
	stderrPipe, err := command.StderrPipe()
	if err != nil {
		return Result{}, fmt.Errorf("Nao foi possivel preparar a captura do stderr do simulador: %w", err)
	}

	if err := command.Start(); err != nil {
		return Result{}, fmt.Errorf("Falha ao iniciar o Java. Verifique o valor de --java-bin e tente novamente: %w", err)
	}

	type stdoutResult struct {
		output string
		err    error
	}

	stdoutCh := make(chan stdoutResult, 1)
	stderrCh := make(chan string, 1)
	waitCh := make(chan error, 1)

	go func() {
		out, readErr := readFirstJSONObject(stdoutPipe)
		stdoutCh <- stdoutResult{out, readErr}
	}()
	go func() {
		data, _ := io.ReadAll(stderrPipe)
		stderrCh <- string(data)
	}()
	go func() {
		waitCh <- command.Wait()
	}()

	select {
	case sr := <-stdoutCh:
		if sr.err == nil && strings.TrimSpace(sr.output) != "" {
			return Result{
				Stdout: sr.output,
				PID:    command.Process.Pid,
			}, nil
		}

		waitErr := <-waitCh
		stderr := <-stderrCh
		result := Result{
			Stdout:   sr.output,
			Stderr:   stderr,
			ExitCode: exitCodeFrom(waitErr),
		}
		if result.ExitCode == 0 {
			return Result{}, fmt.Errorf("O processo Java encerrou sem retornar o JSON de inicializacao do simulador.")
		}
		return result, nil

	case waitErr := <-waitCh:
		stderr := <-stderrCh
		sr := <-stdoutCh
		result := Result{
			Stdout:   sr.output,
			Stderr:   stderr,
			ExitCode: exitCodeFrom(waitErr),
		}
		if result.ExitCode == 0 && strings.TrimSpace(result.Stdout) == "" && strings.TrimSpace(result.Stderr) == "" {
			return Result{}, fmt.Errorf("O processo Java encerrou sem retornar nenhuma saida.")
		}
		return result, nil
	}
}

func (c Config) validate() error {
	if strings.TrimSpace(c.JavaBin) == "" {
		return fmt.Errorf("Nenhum executavel Java foi informado. Use --java-bin para apontar para o Java instalado.")
	}
	if strings.TrimSpace(c.JarPath) == "" {
		return fmt.Errorf("Nenhum caminho de JAR foi informado. Use --jar para apontar para o simulador.jar.")
	}

	if _, err := exec.LookPath(c.JavaBin); err != nil {
		return fmt.Errorf("Java nao encontrado: %s. Verifique o PATH ou informe um caminho valido com --java-bin.", c.JavaBin)
	}

	info, err := os.Stat(c.JarPath)
	if err != nil {
		if os.IsNotExist(err) {
			return fmt.Errorf("simulador.jar nao encontrado: %s. Execute 'simulador-cli iniciar' apos provisionar o JAR ou informe um caminho valido com --jar.", c.JarPath)
		}
		return fmt.Errorf("Nao foi possivel acessar o JAR informado em --jar: %s", c.JarPath)
	}
	if info.IsDir() {
		return fmt.Errorf("O valor informado em --jar precisa apontar para um arquivo, nao para um diretorio: %s", c.JarPath)
	}

	return nil
}

func (c Config) jarArgs(args []string) []string {
	result := make([]string, 0, len(args)+2)
	result = append(result, "-jar", c.JarPath)
	result = append(result, args...)
	return result
}

func exitCodeFrom(err error) int {
	if err == nil {
		return 0
	}
	var exitErr *exec.ExitError
	if errors.As(err, &exitErr) {
		return exitErr.ExitCode()
	}
	return 1
}

func readFirstJSONObject(reader io.Reader) (string, error) {
	buffered := bufio.NewReader(reader)
	var output bytes.Buffer
	started := false
	depth := 0
	inString := false
	escaped := false

	for {
		b, err := buffered.ReadByte()
		if err != nil {
			if !started {
				return "", err
			}
			return output.String(), err
		}

		if !started {
			if b == ' ' || b == '\n' || b == '\r' || b == '\t' {
				continue
			}
			if b != '{' {
				continue
			}
			started = true
			depth = 1
			output.WriteByte(b)
			continue
		}

		output.WriteByte(b)

		if inString {
			if escaped {
				escaped = false
				continue
			}
			if b == '\\' {
				escaped = true
				continue
			}
			if b == '"' {
				inString = false
			}
			continue
		}

		switch b {
		case '"':
			inString = true
		case '{':
			depth++
		case '}':
			depth--
			if depth == 0 {
				return output.String(), nil
			}
		}
	}
}
