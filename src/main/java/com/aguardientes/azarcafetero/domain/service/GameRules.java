package com.aguardientes.azarcafetero.domain.service;

import com.aguardientes.azarcafetero.domain.exception.InvalidMoveException;
import com.aguardientes.azarcafetero.domain.exception.NotPlayerTurnException;
import com.aguardientes.azarcafetero.domain.model.Card;
import com.aguardientes.azarcafetero.domain.model.Game;
import com.aguardientes.azarcafetero.domain.model.Player;

public class GameRules {

    public void validatePlayerTurn(Game game, String playerId) {
        if (!game.isPlayerTurn(playerId)) {
            throw new NotPlayerTurnException(playerId);
        }
    }

    public void validateCardPlay(Game game, String playerId, Card card) {
        Player player = game.getPlayerById(playerId);
        if (player == null) {
            throw new InvalidMoveException("Player not found in game: " + playerId);
        }

        if (!player.getHand().contains(card)) {
            throw new InvalidMoveException("Player does not have this card: " + card);
        }
    }

    public void validateGameStart(Game game) {
        if (!game.canStart()) {
            throw new InvalidMoveException("Cannot start game. Need minimum players or game already started.");
        }
    }

    public boolean canPlayerPlayCard(Player player, Card card) {
        return player.getHand().contains(card);
    }
}
