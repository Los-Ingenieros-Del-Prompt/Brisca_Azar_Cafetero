package com.aguardientes.azarcafetero.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Deck {
    private final List<Card> cards;
    private Card trumpCard;

    public Deck() {
        this.cards = new ArrayList<>();
        initializeDeck();
    }

    private void initializeDeck() {
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                cards.add(new Card(suit, rank));
            }
        }
    }

    public void shuffle() {
        Collections.shuffle(cards);
    }

    public Card draw() {
        if (cards.isEmpty()) {
            return null;
        }
        return cards.remove(0);
    }

    public void setTrumpCard() {
        if (!cards.isEmpty()) {
            this.trumpCard = cards.get(cards.size() - 1);
        }
    }

    public Card getTrumpCard() {
        return trumpCard;
    }

    public Suit getTrumpSuit() {
        return trumpCard != null ? trumpCard.getSuit() : null;
    }

    public int remainingCards() {
        return cards.size();
    }

    public boolean isEmpty() {
        return cards.isEmpty();
    }

    public List<Card> getCards() {
        return new ArrayList<>(cards);
    }
}
