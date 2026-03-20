package com.aguardientes.azarcafetero.application.service;

import com.aguardientes.azarcafetero.application.dto.CardDTO;
import com.aguardientes.azarcafetero.application.dto.GameStateDTO;
import com.aguardientes.azarcafetero.application.dto.PlayerDTO;
import com.aguardientes.azarcafetero.application.dto.TrickDTO;
import com.aguardientes.azarcafetero.domain.model.Card;
import com.aguardientes.azarcafetero.domain.model.Game;
import com.aguardientes.azarcafetero.domain.model.Player;
import com.aguardientes.azarcafetero.domain.model.Trick;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GameMapper {

    public CardDTO toCardDTO(Card card) {
        if (card == null) {
            return null;
        }
        return new CardDTO(
                card.getSuit(),
                card.getRank(),
                card.getPoints()
        );
    }

    public PlayerDTO toPlayerDTO(Player player, boolean includeHand) {
        if (player == null) {
            return null;
        }
        
        List<CardDTO> hand = includeHand 
                ? player.getHand().stream()
                        .map(this::toCardDTO)
                        .toList()
                : List.of();
        
        return new PlayerDTO(
                player.getId(),
                player.getName(),
                player.getScore(),
                hand,
                player.getHandSize()
        );
    }

    public TrickDTO toTrickDTO(Trick trick) {
        if (trick == null) {
            return null;
        }
        
        Map<String, CardDTO> playedCards = trick.getPlayedCards().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> toCardDTO(entry.getValue())
                ));
        
        return new TrickDTO(
                playedCards,
                trick.getLeadPlayerId(),
                trick.getTotalPoints()
        );
    }

    public GameStateDTO toGameStateDTO(Game game, String requestingPlayerId) {
        if (game == null) {
            return null;
        }
        
        List<PlayerDTO> players = game.getPlayers().stream()
                .map(player -> {
                    boolean includeHand = player.getId().equals(requestingPlayerId);
                    return toPlayerDTO(player, includeHand);
                })
                .toList();
        
        Player currentPlayer = game.getCurrentPlayer();
        String currentPlayerId = currentPlayer != null ? currentPlayer.getId() : null;
        
        Player winner = game.getWinner();
        
        return new GameStateDTO(
                game.getId(),
                game.getState(),
                players,
                currentPlayerId,
                toTrickDTO(game.getCurrentTrick()),
                toCardDTO(game.getTrumpCard()),
                game.getTrumpSuit(),
                game.getDeck().remainingCards(),
                toPlayerDTO(winner, false)
        );
    }

    public GameStateDTO toPublicGameStateDTO(Game game) {
        if (game == null) {
            return null;
        }
        
        List<PlayerDTO> players = game.getPlayers().stream()
                .map(player -> toPlayerDTO(player, false))
                .toList();
        
        Player currentPlayer = game.getCurrentPlayer();
        String currentPlayerId = currentPlayer != null ? currentPlayer.getId() : null;
        
        Player winner = game.getWinner();
        
        return new GameStateDTO(
                game.getId(),
                game.getState(),
                players,
                currentPlayerId,
                toTrickDTO(game.getCurrentTrick()),
                toCardDTO(game.getTrumpCard()),
                game.getTrumpSuit(),
                game.getDeck().remainingCards(),
                toPlayerDTO(winner, false)
        );
    }
}
