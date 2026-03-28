package com.aguardientes.azarcafetero.domain.model;

public enum Rank {
    ACE(1, 11, "As"),
    TWO(2, 0, "2"),
    THREE(3, 10, "3"),
    FOUR(4, 0, "4"),
    FIVE(5, 0, "5"),
    SIX(6, 0, "6"),
    SEVEN(7, 0, "7"),
    JACK(10, 2, "Sota"),
    HORSE(11, 3, "Caballo"),
    KING(12, 4, "Rey");

    private final int numericValue;
    private final int points;
    private final String displayName;

    Rank(int numericValue, int points, String displayName) {
        this.numericValue = numericValue;
        this.points = points;
        this.displayName = displayName;
    }

    public int getNumericValue() {
        return numericValue;
    }

    public int getPoints() {
        return points;
    }

    public String getDisplayName() {
        return displayName;
    }
}
