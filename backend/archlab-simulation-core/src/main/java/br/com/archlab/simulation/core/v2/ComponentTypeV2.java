package br.com.archlab.simulation.core.v2;

public enum ComponentTypeV2 {
    CLIENT, WEB_BROWSER, MOBILE_APP, DESKTOP_APP, CLI, BOT, EMBEDDED_SYSTEM, API, DATABASE, BROKER,
    SERVICE, API_GATEWAY, BFF, WORKER, SCHEDULER, AUTHENTICATION_SERVICE,
    WEBHOOK_RECEIVER, ORCHESTRATOR_SERVICE, INTEGRATION_BUS, CACHE,
    OBJECT_STORAGE, NOTIFICATION_SERVICE, SEARCH_ENGINE, SERVICE_REGISTRY, RULES_ENGINE;

    public boolean isClient() {
        return switch (this) {
            case CLIENT, WEB_BROWSER, MOBILE_APP, DESKTOP_APP, CLI, BOT, EMBEDDED_SYSTEM -> true;
            default -> false;
        };
    }

    public boolean isProcessor() {
        return switch (this) {
            case API, SERVICE, API_GATEWAY, BFF, WORKER, SCHEDULER,
                    AUTHENTICATION_SERVICE, WEBHOOK_RECEIVER, ORCHESTRATOR_SERVICE,
                    INTEGRATION_BUS, NOTIFICATION_SERVICE -> true;
            default -> false;
        };
    }

    public boolean isVirtualResource() {
        return switch (this) {
            case CACHE, OBJECT_STORAGE, SEARCH_ENGINE, SERVICE_REGISTRY, RULES_ENGINE -> true;
            default -> false;
        };
    }

    public boolean receivesRequest() {
        return isVirtualResource() || switch(this) {
            case API,SERVICE,API_GATEWAY,BFF,SCHEDULER,AUTHENTICATION_SERVICE,
                    WEBHOOK_RECEIVER,ORCHESTRATOR_SERVICE,INTEGRATION_BUS,NOTIFICATION_SERVICE -> true;
            default -> false;
        };
    }

    public boolean consumesMessage() {
        return switch(this) {
            case API,SERVICE,WORKER,NOTIFICATION_SERVICE,ORCHESTRATOR_SERVICE,INTEGRATION_BUS -> true;
            default -> false;
        };
    }

    public boolean initiatesRequest() {
        return isClient() || switch(this) {
            case API,SERVICE,API_GATEWAY,BFF,WORKER,SCHEDULER,ORCHESTRATOR_SERVICE,
                    WEBHOOK_RECEIVER,INTEGRATION_BUS,AUTHENTICATION_SERVICE -> true;
            default -> false;
        };
    }

    public boolean publishesMessage() {
        return isClient() || switch(this) {
            case API,SERVICE,WORKER,SCHEDULER,ORCHESTRATOR_SERVICE,WEBHOOK_RECEIVER,
                    INTEGRATION_BUS,NOTIFICATION_SERVICE -> true;
            default -> false;
        };
    }
}
