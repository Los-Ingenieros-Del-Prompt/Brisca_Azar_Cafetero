package com.aguardientes.azarcafetero.domain.service;

import com.aguardientes.azarcafetero.domain.model.Player;
import com.aguardientes.azarcafetero.domain.model.Trick;

import java.util.List;

public class ScoreCalculator {

    public int calculateTrickPoints(Trick trick) {
        return trick.getTotalPoints();
    }

    public int calculatePlayerScore(Player player) {
        return player.getScore();
    }

    public Player findWinner(List<Player> players) {
        return players.stream()
                .max((p1, p2) -> Integer.compare(p1.getScore(), p2.getScore()))
                .orElse(null);
    }

    public List<Player> getRanking(List<Player> players) {
        return players.stream()
                .sorted((p1, p2) -> Integer.compare(p2.getScore(), p1.getScore()))
                .toList();
    }
}
