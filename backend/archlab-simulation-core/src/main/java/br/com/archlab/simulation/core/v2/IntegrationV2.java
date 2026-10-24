package br.com.archlab.simulation.core.v2;

/** Template data only: never executed as HTTP, SOAP, SQL or scripts. */
public sealed interface IntegrationV2
        permits HttpRestIntegrationV2, SoapHttpIntegrationV2, DatabaseQueryIntegrationV2 {
    IntegrationKindV2 kind();
}
