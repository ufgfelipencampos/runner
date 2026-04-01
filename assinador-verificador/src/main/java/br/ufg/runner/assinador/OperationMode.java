package br.ufg.runner.assinador;

enum OperationMode {
    ONE_TIME("one-time");

    private final String cliName;

    OperationMode(String cliName) {
        this.cliName = cliName;
    }

    String cliName() {
        return cliName;
    }

    static OperationMode fromCliName(String rawValue) throws ValidationException {
        for (OperationMode value : values()) {
            if (value.cliName.equalsIgnoreCase(rawValue)) {
                return value;
            }
        }

        throw new ValidationException("Modo nao suportado: " + rawValue + ". Nesta versao use apenas one-time.");
    }
}

