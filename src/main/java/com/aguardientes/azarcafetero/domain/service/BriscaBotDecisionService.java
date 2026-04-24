package com.aguardientes.azarcafetero.domain.service;

import com.aguardientes.azarcafetero.domain.model.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Servicio de dominio que decide qué carta debe jugar el bot en cada turno.
 *
 * Separado del motor de juego (GameService) para que la lógica sea
 * testeable en aislamiento sin infraestructura.
 *
 * Uso:
 *   Card card = botDecisionService.decide(game, botPlayerId, difficulty);
 *   gameService.playCard(new PlayCardCommand(gameId, botPlayerId, card.getSuit(), card.getRank()));
 */
public class BriscaBotDecisionService {

    // Umbral de puntos en la baza para que valga la pena gastar un triunfo
    private static final int TRUMP_WORTH_THRESHOLD = 5;

    private final Random random;

    public BriscaBotDecisionService() {
        this.random = new Random();
    }

    // Constructor para tests (inyección de Random controlado)
    public BriscaBotDecisionService(Random random) {
        this.random = random;
    }

    /**
     * Punto de entrada principal.
     *
     * @param game       Estado actual de la partida
     * @param botId      ID del jugador bot (debe ser el turno activo)
     * @param difficulty Nivel de dificultad
     * @return Carta a jugar (siempre válida, nunca null)
     */
    public Card decide(Game game, String botId, BotDifficulty difficulty) {
        Player bot = game.getPlayerById(botId);
        if (bot == null) {
            throw new IllegalArgumentException("Bot player not found: " + botId);
        }

        List<Card> hand = bot.getHand();
        if (hand.isEmpty()) {
            throw new IllegalStateException("Bot has no cards to play");
        }

        // Con una sola carta no hay decisión que tomar
        if (hand.size() == 1) {
            return hand.get(0);
        }

        return switch (difficulty) {
            case EASY   -> decideEasy(hand);
            case MEDIUM -> decideMedium(hand, game);
            case HARD   -> decideHard(hand, game);
        };
    }

    // ─── EASY: aleatoriedad pura ──────────────────────────────────────────────

    private Card decideEasy(List<Card> hand) {
        return hand.get(random.nextInt(hand.size()));
    }

    // ─── MEDIUM ───────────────────────────────────────────────────────────────

    private Card decideMedium(List<Card> hand, Game game) {
        Trick trick = game.getCurrentTrick();
        Suit trumpSuit = game.getTrumpSuit();

        // El bot lidera la baza: juega la carta más barata (no triunfo si puede evitarlo)
        if (isLeading(trick)) {
            return lowestValueCard(hand, trumpSuit, false);
        }

        // El bot sigue: intenta ganar si vale la pena, si no descarta lo más barato
        Card leadCard = trick.getPlayedCards().get(trick.getLeadPlayerId());
        return followCardMedium(hand, leadCard, trumpSuit, trick.getTotalPoints());
    }

    private Card followCardMedium(List<Card> hand, Card leadCard,
                                  Suit trumpSuit, int trickPoints) {
        // Intenta ganar con una carta no-triunfo del mismo palo
        Optional<Card> winnerSameSuit = hand.stream()
                .filter(c -> c.getSuit() == leadCard.getSuit())
                .filter(c -> c.getNumericValue() > leadCard.getNumericValue())
                .min(Comparator.comparingInt(Card::getNumericValue)); // mínima que gana

        if (winnerSameSuit.isPresent()) {
            return winnerSameSuit.get();
        }

        // Si la baza tiene suficientes puntos, considera usar un triunfo
        if (trickPoints >= TRUMP_WORTH_THRESHOLD && leadCard.getSuit() != trumpSuit) {
            Optional<Card> lowestTrump = hand.stream()
                    .filter(c -> c.getSuit() == trumpSuit)
                    .min(Comparator.comparingInt(Card::getNumericValue));

            if (lowestTrump.isPresent()) {
                return lowestTrump.get();
            }
        }

        // No puede ganar o no vale la pena: descarta lo más barato
        return lowestValueCard(hand, trumpSuit, true);
    }

    // ─── HARD ─────────────────────────────────────────────────────────────────

    private Card decideHard(List<Card> hand, Game game) {
        Trick trick       = game.getCurrentTrick();
        Suit trumpSuit    = game.getTrumpSuit();
        boolean isWinning = isBotWinning(game);

        if (isLeading(trick)) {
            return leadCardHard(hand, trumpSuit, isWinning);
        }

        Card leadCard = trick.getPlayedCards().get(trick.getLeadPlayerId());
        return followCardHard(hand, leadCard, trumpSuit, trick.getTotalPoints(), isWinning);
    }

    /**
     * Estrategia de apertura (HARD):
     * - Si va ganando: juega conservador (carta más barata sin triunfo)
     * - Si va perdiendo: ataca con carta de alto valor para capturar puntos
     */
    private Card leadCardHard(List<Card> hand, Suit trumpSuit, boolean isWinning) {
        if (isWinning) {
            // Defensivo: no arriesga triunfos
            return lowestValueCard(hand, trumpSuit, false);
        }

        // Ofensivo: juega la carta de mayor valor (no triunfo primero para reservarlo)
        Optional<Card> highNonTrump = hand.stream()
                .filter(c -> c.getSuit() != trumpSuit)
                .max(Comparator.comparingInt(Card::getPoints));

        return highNonTrump.orElseGet(() ->
                // Solo tiene triunfos: juega el de más valor
                hand.stream()
                        .max(Comparator.comparingInt(Card::getPoints))
                        .orElse(hand.get(0))
        );
    }

    /**
     * Estrategia de seguimiento (HARD):
     * - Intenta ganar con la carta mínima necesaria
     * - Usa triunfo solo si los puntos en juego lo justifican (>= umbral)
     * - Si no puede ganar, descarta la carta de menos valor (sin puntos)
     */
    private Card followCardHard(List<Card> hand, Card leadCard,
                                Suit trumpSuit, int trickPoints, boolean isWinning) {
        // 1. ¿Puede ganar con el mismo palo con la carta mínima?
        Optional<Card> minWinnerSameSuit = hand.stream()
                .filter(c -> c.getSuit() == leadCard.getSuit())
                .filter(c -> c.getNumericValue() > leadCard.getNumericValue())
                .min(Comparator.comparingInt(Card::getNumericValue));

        if (minWinnerSameSuit.isPresent()) {
            // Gana con el mismo palo, siempre conviene
            return minWinnerSameSuit.get();
        }

        // 2. ¿Vale la pena usar un triunfo?
        boolean leadIsTrump        = leadCard.getSuit() == trumpSuit;
        boolean trickWorthTrumping = trickPoints >= TRUMP_WORTH_THRESHOLD;

        if (!leadIsTrump && trickWorthTrumping) {
            // Busca el triunfo más bajo que gane (si el líder no es triunfo)
            Optional<Card> lowestTrump = hand.stream()
                    .filter(c -> c.getSuit() == trumpSuit)
                    .min(Comparator.comparingInt(Card::getNumericValue));

            if (lowestTrump.isPresent()) {
                return lowestTrump.get();
            }
        }

        // 3. Si lidera con triunfo, necesita un triunfo mayor para ganar
        if (leadIsTrump) {
            Optional<Card> higherTrump = hand.stream()
                    .filter(c -> c.getSuit() == trumpSuit)
                    .filter(c -> c.getNumericValue() > leadCard.getNumericValue())
                    .min(Comparator.comparingInt(Card::getNumericValue));

            if (higherTrump.isPresent() && trickWorthTrumping) {
                return higherTrump.get();
            }
        }

        // 4. No puede / no conviene ganar: descarta la carta de menor valor
        return lowestValueCard(hand, trumpSuit, true);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /**
     * ¿El bot lidera la baza actual? (nadie ha jugado todavía)
     */
    private boolean isLeading(Trick trick) {
        return trick.getPlayedCards().isEmpty();
    }

    /**
     * Devuelve la carta de menor valor de la mano.
     *
     * @param avoidTrumps Si es true, prefiere cartas que no sean triunfo;
     *                    si solo tiene triunfos, devuelve el triunfo más barato.
     */
    private Card lowestValueCard(List<Card> hand, Suit trumpSuit, boolean avoidTrumps) {
        if (avoidTrumps) {
            // Primero intenta una no-triunfo de bajo valor (0 puntos)
            Optional<Card> cheapNonTrump = hand.stream()
                    .filter(c -> c.getSuit() != trumpSuit)
                    .filter(c -> c.getPoints() == 0)
                    .findFirst();

            if (cheapNonTrump.isPresent()) return cheapNonTrump.get();

            // Si no hay cero-puntos no-triunfo, la de menor valor en general (sin triunfo)
            Optional<Card> lowestNonTrump = hand.stream()
                    .filter(c -> c.getSuit() != trumpSuit)
                    .min(Comparator.comparingInt(Card::getPoints));

            if (lowestNonTrump.isPresent()) return lowestNonTrump.get();
        }

        // Fallback: la de menor puntos de toda la mano (puede ser triunfo)
        return hand.stream()
                .min(Comparator.comparingInt(Card::getPoints))
                .orElse(hand.get(0));
    }

    /**
     * Comprueba si el bot está ganando la partida en puntuación.
     * Se usa para decidir si jugar defensivo u ofensivo.
     */
    private boolean isBotWinning(Game game) {
        Player bot = game.getCurrentPlayer();
        if (bot == null) return false;

        return game.getPlayers().stream()
                .filter(p -> !p.getId().equals(bot.getId()))
                .allMatch(p -> bot.getScore() > p.getScore());
    }
}
