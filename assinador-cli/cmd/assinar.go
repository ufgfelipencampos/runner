package cmd

import (
	"github.com/spf13/cobra"
)

type assinarOptions struct {
	runtimeFlags
	Entrada          string
	Saida            string
	Modo             string
	Alias            string
	BibliotecaPKCS11 string
	SlotPKCS11       string
	Porta            int
}

func newAssinarCmd() *cobra.Command {
	options := &assinarOptions{}

	command := &cobra.Command{
		Use:   "assinar",
		Short: "Executa o comando sign do assinador-verificador.jar",
		Args:  cobra.NoArgs,
		Example: "  assinador-cli assinar --entrada ./entrada.json --saida ./assinado.json --modo direto --alias demo\n" +
			"  assinador-cli assinar --entrada ./entrada.json --saida ./assinado.json --modo http --porta 8080 --alias demo",
		RunE: options.run,
	}

	flags := command.Flags()
	flags.StringVar(&options.Entrada, "entrada", "", "Arquivo JSON de entrada.")
	flags.StringVar(&options.Saida, "saida", "", "Arquivo JSON de saida.")
	flags.StringVar(&options.Modo, "modo", "", "Modo de execucao: direto ou http.")
	flags.StringVar(&options.Alias, "alias", "", "Alias do assinador.")
	flags.StringVar(&options.BibliotecaPKCS11, "biblioteca-pkcs11", "", "Biblioteca PKCS#11 opcional.")
	flags.StringVar(&options.SlotPKCS11, "slot-pkcs11", "", "Slot PKCS#11 opcional.")
	flags.IntVar(&options.Porta, "porta", defaultPort, "Porta do servidor HTTP quando --modo http for usado.")
	bindRuntimeFlags(command, &options.runtimeFlags)

	return command
}

func (o *assinarOptions) run(command *cobra.Command, _ []string) error {
	if err := ensureValidJSONPaths(o.Entrada, o.Saida); err != nil {
		return err
	}
	if err := ensureValidAlias(o.Alias); err != nil {
		return err
	}
	if err := ensureValidPKCS11Library(o.BibliotecaPKCS11); err != nil {
		return err
	}
	if err := ensureValidPKCS11Slot(o.SlotPKCS11); err != nil {
		return err
	}

	mode, err := modeToJarValue(o.Modo)
	if err != nil {
		return err
	}
	if err := ensurePort(command, mode, o.Porta); err != nil {
		return err
	}

	args := []string{
		"sign",
		"--pathin", o.Entrada,
		"--pathout", o.Saida,
		"--mode", mode,
		"--alias", o.Alias,
	}
	args = appendPortIfNeeded(args, mode, o.Porta)

	if o.BibliotecaPKCS11 != "" {
		args = append(args, "--pkcs11-lib", o.BibliotecaPKCS11)
	}
	if o.SlotPKCS11 != "" {
		args = append(args, "--pkcs11-slot", o.SlotPKCS11)
	}

	result, err := newRunnerConfig(o.runtimeFlags).Run(args)
	if err != nil {
		return wrapRuntimeError(err)
	}

	return emitResult(command.OutOrStdout(), command.ErrOrStderr(), result)
}
