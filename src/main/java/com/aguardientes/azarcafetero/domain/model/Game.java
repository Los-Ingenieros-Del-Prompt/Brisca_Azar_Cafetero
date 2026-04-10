package com.aguardientes.azarcafetero.domain.model;

import com.aguardientes.azarcafetero.domain.exception.GameFullException;
import com.aguardientes.azarcafetero.domain.exception.GameNotStartedException;
import com.aguardientes.azarcafetero.domain.exception.PlayerNotInGameException;

import java.math.BigDecimal;
import java.util.*;

public class Game {
    private final String id;
    private final int minPlayers;
    private final int maxPlayers;
    private final BigDecimal betAmount;
    private final List<Player> players;
    private final Deck deck;
    private final Trick currentTrick;
    private GameState state;
    private int currentPlayerIndex;
    private final Object playerLock = new Object();

    public Game(String id, int minPlayers, int maxPlayers, BigDecimal betAmount) {
        this.id = Objects.requireNonNull(id, "Game ID cannot be null");
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
        this.betAmount = Objects.requireNonNull(betAmount, "Bet amount cannot be null");
        this.players = new ArrayList<>();
        this.deck = new Deck();
        this.currentTrick = new Trick();
        this.state = GameState.WAITING_FOR_PLAYERS;
        this.currentPlayerIndex = 0;
    }

    public String getId() { return id; }
    public GameState getState() { return state; }
    public BigDecimal getBetAmount() { return betAmount; }

    public List<Player> getPlayers() {
        return new ArrayList<>(players);
    }

    public Deck getDeck() { return deck; }
    public Trick getCurrentTrick() { return currentTrick; }
    public int getCurrentPlayerIndex() { return currentPlayerIndex; }

    public Player getCurrentPlayer() {
        if (players.isEmpty()) return null;
        return players.get(currentPlayerIndex);
    }

    public synchronized void addPlayer(Player player) {
        synchronized (playerLock) {
            if (state != GameState.WAITING_FOR_PLAYERS) {
                throw new IllegalStateException("Cannot add players once game has started");
            }
            if (players.size() >= maxPlayers) {
                throw new GameFullException(id);
            }
            if (players.stream().anyMatch(p -> p.getId().equals(player.getId()))) {
                throw new IllegalArgumentException("Player already in game: " + player.getId());
            }
            players.add(player);
        }
    }

    public void start() {
        if (players.size() < minPlayers) {
            throw new IllegalStateException("Not enough players to start game. Minimum: " + minPlayers);
        }
        if (state != GameState.WAITING_FOR_PLAYERS) {
            throw new IllegalStateException("Game has already started");
        }
        deck.shuffle();
        deck.setTrumpCard();
        dealInitialCards();
        state = GameState.IN_PROGRESS;
    }

    private void dealInitialCards() {
        for (int i = 0; i < 3; i++) {
            for (Player player : players) {
                Card card = deck.draw();
                if (card != null) player.addCard(card);
            }
        }
    }

    public void playCard(String playerId, Card card) {
        if (state != GameState.IN_PROGRESS) throw new GameNotStartedException(id);
        Player player = getPlayerById(playerId);
        if (player == null) throw new PlayerNotInGameException(playerId, id);
        if (!isPlayerTurn(playerId)) throw new IllegalStateException("Not player's turn: " + playerId);
        Card playedCard = player.playCard(card);
        currentTrick.addCard(playerId, playedCard);
        moveToNextPlayer();
    }

    public boolean isTrickComplete() {
        return currentTrick.isComplete(players.size());
    }

    public void resolveTrick(String winnerId) {
        Player winner = getPlayerById(winnerId);
        if (winner == null) throw new PlayerNotInGameException(winnerId, id);
        int points = currentTrick.getTotalPoints();
        winner.addPoints(points);
        drawCardsAfterTrick(winnerId);
        currentTrick.clear();
        setCurrentPlayerByIndex(players.indexOf(winner));
    }

    private void drawCardsAfterTrick(String winnerPlayerId) {
        Player winner = getPlayerById(winnerPlayerId);
        int winnerIndex = players.indexOf(winner);
        for (int i = 0; i < players.size(); i++) {
            int playerIndex = (winnerIndex + i) % players.size();
            Player player = players.get(playerIndex);
            Card drawnCard = deck.draw();
            if (drawnCard != null) player.addCard(drawnCard);
        }
    }

    public boolean isGameOver() {
        return deck.isEmpty() && players.stream().noneMatch(Player::hasCards);
    }

    public void finish() { state = GameState.FINISHED; }

    public Player getWinner() {
        if (state != GameState.FINISHED) return null;
        return players.stream()
                .max(Comparator.comparingInt(Player::getScore))
                .orElse(null);
    }

    public List<Player> getRanking() {
        return players.stream()
                .sorted(Comparator.comparingInt(Player::getScore).reversed())
                .toList();
    }

    public Player getPlayerById(String playerId) {
        return players.stream()
                .filter(p -> p.getId().equals(playerId))
                .findFirst()
                .orElse(null);
    }

    public boolean isPlayerTurn(String playerId) {
        Player currentPlayer = getCurrentPlayer();
        return currentPlayer != null && currentPlayer.getId().equals(playerId);
    }

    private void moveToNextPlayer() {
        currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
    }

    private void setCurrentPlayerByIndex(int index) {
        this.currentPlayerIndex = index;
    }

    public Suit getTrumpSuit() { return deck.getTrumpSuit(); }
    public Card getTrumpCard() { return deck.getTrumpCard(); }
    public int getPlayerCount() { return players.size(); }

    public boolean canStart() {
        return players.size() >= minPlayers && state == GameState.WAITING_FOR_PLAYERS;
    }
}