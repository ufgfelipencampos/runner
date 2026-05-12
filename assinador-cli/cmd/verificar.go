package cmd

import "github.com/spf13/cobra"

type verificarOptions struct {
	runtimeFlags
	Entrada          string
	Saida            string
	Modo             string
	Alias            string
	BibliotecaPKCS11 string
	SlotPKCS11       string
	Porta            int
}

func newVerificarCmd() *cobra.Command {
	options := &verificarOptions{}

	command := &cobra.Command{
		Use:   "verificar",
		Short: "Executa o comando validate do assinador-verificador.jar",
		Args:  cobra.NoArgs,
		Example: "  assinador-cli verificar --entrada ./assinado.json --saida ./resultado.json --modo direto\n" +
			"  assinador-cli verificar --entrada ./assinado.json --saida ./resultado.json --modo http --porta 8080",
		RunE: options.run,
	}

	flags := command.Flags()
	flags.StringVar(&options.Entrada, "entrada", "", "Arquivo JSON de entrada.")
	flags.StringVar(&options.Saida, "saida", "", "Arquivo JSON de saida.")
	flags.StringVar(&options.Modo, "modo", "", "Modo de execucao: direto ou http.")
	flags.StringVar(&options.Alias, "alias", "", "Nao suportado em verificar; mantido apenas para detectar uso invalido.")
	flags.StringVar(&options.BibliotecaPKCS11, "biblioteca-pkcs11", "", "Nao suportado em verificar; mantido apenas para detectar uso invalido.")
	flags.StringVar(&options.SlotPKCS11, "slot-pkcs11", "", "Nao suportado em verificar; mantido apenas para detectar uso invalido.")
	flags.IntVar(&options.Porta, "porta", defaultPort, "Porta do servidor HTTP quando --modo http for usado.")
	bindRuntimeFlags(command, &options.runtimeFlags)

	return command
}

func (o *verificarOptions) run(command *cobra.Command, _ []string) error {
	if err := ensureValidJSONPaths(o.Entrada, o.Saida); err != nil {
		return err
	}
	if command.Flags().Changed("alias") || command.Flags().Changed("biblioteca-pkcs11") || command.Flags().Changed("slot-pkcs11") {
		return validationError("O comando verificar nao aceita --alias, --biblioteca-pkcs11 nem --slot-pkcs11.")
	}

	mode, err := modeToJarValue(o.Modo)
	if err != nil {
		return err
	}
	if err := ensurePort(command, mode, o.Porta); err != nil {
		return err
	}

	args := []string{
		"validate",
		"--pathin", o.Entrada,
		"--pathout", o.Saida,
		"--mode", mode,
	}
	args = appendPortIfNeeded(args, mode, o.Porta)

	result, err := newRunnerConfig(o.runtimeFlags).Run(args)
	if err != nil {
		return wrapRuntimeError(err)
	}

	return emitResult(command.OutOrStdout(), command.ErrOrStderr(), result)
}
