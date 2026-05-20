package cmd

import (
	"strconv"

	"github.com/spf13/cobra"
)

type iniciarOptions struct {
	runtimeFlags
	Porta int
}

func newIniciarCmd() *cobra.Command {
	options := &iniciarOptions{}

	command := &cobra.Command{
		Use:   "iniciar",
		Short: "Inicia o simulador.jar na porta indicada",
		Args:  cobra.NoArgs,
		Example: "  simulador-cli iniciar\n" +
			"  simulador-cli iniciar --porta 8443",
		RunE: options.run,
	}

	flags := command.Flags()
	flags.IntVar(&options.Porta, "porta", defaultPort, "Porta em que o simulador sera iniciado.")
	bindRuntimeFlags(command, &options.runtimeFlags)

	return command
}

func (o *iniciarOptions) run(command *cobra.Command, _ []string) error {
	if o.Porta < 1 || o.Porta > 65535 {
		return validationError("A porta informada em --porta deve estar entre 1 e 65535.")
	}

	args := []string{"server", "start", "--port", strconv.Itoa(o.Porta)}

	result, err := newRunnerConfig(o.runtimeFlags).StartServer(args)
	if err != nil {
		return wrapRuntimeError(err)
	}

	return emitResult(command.OutOrStdout(), command.ErrOrStderr(), result)
}
