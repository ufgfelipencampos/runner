package br.ufg.runner.assinador;

enum ServerCommandType {
    START("start"),
    STATUS("status"),
    STOP("stop");

    private final String cliName;

    ServerCommandType(String cliName) {
        this.cliName = cliName;
    }

    String cliName() {
        return cliName;
    }

    static ServerCommandType fromCliName(String rawValue) throws ValidationException {
        for (ServerCommandType value : values()) {
            if (value.cliName.equalsIgnoreCase(rawValue)) {
                return value;
            }
        }

        throw new ValidationException("Acao invalida para o comando server: " + rawValue + ". Use start, status ou stop.");
    }
}
