package com.aguardientes.azarcafetero.domain.model;

public enum Rank {
    ACE(11, 11, "As"),
    TWO(1, 0, "2"),
    THREE(10, 10, "3"),
    FOUR(2, 0, "4"),
    FIVE(3, 0, "5"),
    SIX(4, 0, "6"),
    SEVEN(5, 0, "7"),
    JACK(6, 2, "Sota"),
    HORSE(7, 3, "Caballo"),
    KING(8, 4, "Rey");

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
