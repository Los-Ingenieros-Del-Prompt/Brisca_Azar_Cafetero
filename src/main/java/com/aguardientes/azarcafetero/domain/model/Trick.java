package com.aguardientes.azarcafetero.domain.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Trick {
    private final Map<String, Card> playedCards;
    private String leadPlayerId;

    public Trick() {
        this.playedCards = new LinkedHashMap<>();
    }

    public void addCard(String playerId, Card card) {
        if (playedCards.isEmpty()) {
            this.leadPlayerId = playerId;
        }
        playedCards.put(playerId, card);
    }

    public Card getCardByPlayer(String playerId) {
        return playedCards.get(playerId);
    }

    public String getLeadPlayerId() {
        return leadPlayerId;
    }

    public Suit getLeadSuit() {
        if (leadPlayerId == null || !playedCards.containsKey(leadPlayerId)) {
            return null;
        }
        return playedCards.get(leadPlayerId).getSuit();
    }

    public Map<String, Card> getPlayedCards() {
        return new LinkedHashMap<>(playedCards);
    }

    public List<Card> getAllCards() {
        return new ArrayList<>(playedCards.values());
    }

    public int getTotalPoints() {
        return playedCards.values().stream()
                .mapToInt(Card::getPoints)
                .sum();
    }

    public boolean isComplete(int numberOfPlayers) {
        return playedCards.size() == numberOfPlayers;
    }

    public int getPlayedCardCount() {
        return playedCards.size();
    }

    public void clear() {
        playedCards.clear();
        leadPlayerId = null;
    }
}
