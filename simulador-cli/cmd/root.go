package cmd

import "github.com/spf13/cobra"

func NewRootCommand() *cobra.Command {
	rootCmd := &cobra.Command{
		Use:           "simulador-cli",
		Short:         "CLI para gerenciar o ciclo de vida do simulador.jar",
		SilenceUsage:  true,
		SilenceErrors: true,
	}

	rootCmd.AddCommand(
		newIniciarCmd(),
		newPararCmd(),
		newStatusCmd(),
	)

	return rootCmd
}
