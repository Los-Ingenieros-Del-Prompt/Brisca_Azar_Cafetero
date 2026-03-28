package com.aguardientes.azarcafetero.domain.model;

import com.aguardientes.azarcafetero.domain.exception.InvalidMoveException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Player {
    private final String id;
    private final String name;
    private final List<Card> hand;
    private int score;

    public Player(String id, String name) {
        this.id = Objects.requireNonNull(id, "Player ID cannot be null");
        this.name = Objects.requireNonNull(name, "Player name cannot be null");
        this.hand = new ArrayList<>();
        this.score = 0;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public List<Card> getHand() {
        return new ArrayList<>(hand);
    }

    public int getScore() {
        return score;
    }

    public void addCard(Card card) {
        if (card != null) {
            hand.add(card);
        }
    }

    public Card playCard(Card card) {
        if (!hand.contains(card)) {
            throw new InvalidMoveException("Player does not have card: " + card);
        }
        hand.remove(card);
        return card;
    }

    public void addPoints(int points) {
        this.score += points;
    }

    public int getHandSize() {
        return hand.size();
    }

    public boolean hasCards() {
        return !hand.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Player player = (Player) o;
        return Objects.equals(id, player.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Player{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", score=" + score +
                ", handSize=" + hand.size() +
                '}';
    }
}
