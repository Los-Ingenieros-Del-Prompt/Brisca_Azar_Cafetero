package com.aguardientes.azarcafetero.domain.model;

import java.util.Objects;

public class Card {
    private final Suit suit;
    private final Rank rank;

    public Card(Suit suit, Rank rank) {
        this.suit = Objects.requireNonNull(suit, "Suit cannot be null");
        this.rank = Objects.requireNonNull(rank, "Rank cannot be null");
    }

    public Suit getSuit() {
        return suit;
    }

    public Rank getRank() {
        return rank;
    }

    public int getPoints() {
        return rank.getPoints();
    }

    public int getNumericValue() {
        return rank.getNumericValue();
    }

    public boolean isTrump(Suit trumpSuit) {
        return this.suit == trumpSuit;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Card card = (Card) o;
        return suit == card.suit && rank == card.rank;
    }

    @Override
    public int hashCode() {
        return Objects.hash(suit, rank);
    }

    @Override
    public String toString() {
        return rank.getDisplayName() + " de " + suit.getDisplayName();
    }
}
