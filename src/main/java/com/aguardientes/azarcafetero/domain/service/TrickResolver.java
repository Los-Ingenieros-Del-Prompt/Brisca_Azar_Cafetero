package com.aguardientes.azarcafetero.domain.service;

import com.aguardientes.azarcafetero.domain.model.Card;
import com.aguardientes.azarcafetero.domain.model.Suit;
import com.aguardientes.azarcafetero.domain.model.Trick;

import java.util.Map;

public class TrickResolver {

    public String determineWinner(Trick trick, Suit trumpSuit) {
        Map<String, Card> playedCards = trick.getPlayedCards();
        Suit leadSuit = trick.getLeadSuit();

        String winnerId = null;
        Card winningCard = null;

        for (Map.Entry<String, Card> entry : playedCards.entrySet()) {
            String playerId = entry.getKey();
            Card card = entry.getValue();

            if (winningCard == null) {
                winnerId = playerId;
                winningCard = card;
                continue;
            }

            if (isCardStronger(card, winningCard, leadSuit, trumpSuit)) {
                winnerId = playerId;
                winningCard = card;
            }
        }

        return winnerId;
    }

    private boolean isCardStronger(Card challenger, Card current, Suit leadSuit, Suit trumpSuit) {
        boolean challengerIsTrump = challenger.isTrump(trumpSuit);
        boolean currentIsTrump = current.isTrump(trumpSuit);

        if (challengerIsTrump && !currentIsTrump) {
            return true;
        }

        if (!challengerIsTrump && currentIsTrump) {
            return false;
        }

        if (challengerIsTrump && currentIsTrump) {
            return challenger.getNumericValue() > current.getNumericValue();
        }

        boolean challengerIsLeadSuit = challenger.getSuit() == leadSuit;
        boolean currentIsLeadSuit = current.getSuit() == leadSuit;

        if (challengerIsLeadSuit && !currentIsLeadSuit) {
            return true;
        }

        if (!challengerIsLeadSuit && currentIsLeadSuit) {
            return false;
        }

        if (challengerIsLeadSuit && currentIsLeadSuit) {
            return challenger.getNumericValue() > current.getNumericValue();
        }

        return false;
    }
}
