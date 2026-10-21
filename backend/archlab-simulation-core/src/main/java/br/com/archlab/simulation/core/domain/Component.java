package br.com.archlab.simulation.core.domain;

public sealed interface Component permits ClientComponent, ApiComponent, DatabaseComponent {
    String id();
    ComponentType type();
}
