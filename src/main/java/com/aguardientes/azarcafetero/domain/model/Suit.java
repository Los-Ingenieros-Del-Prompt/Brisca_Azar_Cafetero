package com.aguardientes.azarcafetero.domain.model;

public enum Suit {
    OROS("Oros"),
    COPAS("Copas"),
    ESPADAS("Espadas"),
    BASTOS("Bastos");

    private final String displayName;

    Suit(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
