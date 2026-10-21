package br.com.archlab.simulation.core.validation;

public final class InvalidArchitectureException extends IllegalArgumentException {
    private final ValidationResult result;

    public InvalidArchitectureException(ValidationResult result) {
        super("Architecture is invalid: " + result.violations().size() + " violation(s)");
        this.result = result;
    }

    public ValidationResult result() { return result; }
}
