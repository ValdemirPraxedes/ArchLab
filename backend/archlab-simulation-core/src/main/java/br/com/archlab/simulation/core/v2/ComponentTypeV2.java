package br.com.archlab.simulation.core.v2;

public enum ComponentTypeV2 {
    CLIENT, WEB_BROWSER, MOBILE_APP, DESKTOP_APP, CLI, BOT, EMBEDDED_SYSTEM, API, DATABASE, BROKER;

    public boolean isClient() {
        return switch (this) {
            case CLIENT, WEB_BROWSER, MOBILE_APP, DESKTOP_APP, CLI, BOT, EMBEDDED_SYSTEM -> true;
            default -> false;
        };
    }
}
