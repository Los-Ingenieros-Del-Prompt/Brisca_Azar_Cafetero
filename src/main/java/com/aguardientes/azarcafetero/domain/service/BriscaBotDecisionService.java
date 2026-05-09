package com.aguardientes.azarcafetero.domain.service;

import com.aguardientes.azarcafetero.domain.model.*;

import java.util.*;

/**
 * Servicio de decisión del bot de Brisca.
 *
 * ┌─────────┬──────────────────────────────────────────────────────────────┐
 * │ EASY    │ Selección aleatoria pura. Sin estrategia.                    │
 * ├─────────┼──────────────────────────────────────────────────────────────┤
 * │ MEDIUM  │ Búsqueda greedy con función heurística (profundidad 1).      │
 * │         │ Evalúa cada carta con un score y elige la de mayor valor.    │
 * ├─────────┼──────────────────────────────────────────────────────────────┤
 * │ HARD    │ Minimax con poda Alpha-Beta (profundidad 2).                 │
 * │         │ Explora árbol de decisiones asumiendo que el oponente        │
 * │         │ juega de forma óptima (minimiza la utilidad del bot).        │
 * └─────────┴──────────────────────────────────────────────────────────────┘
 */
public class BriscaBotDecisionService {


    /** Profundidad del árbol Minimax para HARD. */
    private static final int MINIMAX_DEPTH = 2;

    /** Penalización por desperdiciar un triunfo sin ganar la baza. */
    private static final double TRUMP_WASTE_PENALTY = 3.5;

    /** Multiplicador de ganancia al ganar una baza. */
    private static final double WIN_MULTIPLIER = 1.5;

    /** Penalización al perder una carta de alto valor. */
    private static final double LOSS_MULTIPLIER = 1.2;

    private final Random random;

    public BriscaBotDecisionService() {
        this.random = new Random();
    }

    public BriscaBotDecisionService(Random random) {
        this.random = random;
    }


    public Card decide(Game game, String botId, BotDifficulty difficulty) {
        Player bot = game.getPlayerById(botId);
        if (bot == null) throw new IllegalArgumentException("Bot not found: " + botId);

        List<Card> hand = new ArrayList<>(bot.getHand());
        if (hand.isEmpty()) throw new IllegalStateException("Bot has no cards");
        if (hand.size() == 1) return hand.get(0);

        return switch (difficulty) {
            case EASY   -> decideEasy(hand);
            case MEDIUM -> decideMediumGreedy(hand, game, botId);
            case HARD   -> decideHardMinimax(hand, game, botId);
        };
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // EASY — Selección aleatoria
    // ═══════════════════════════════════════════════════════════════════════════

    private Card decideEasy(List<Card> hand) {
        return hand.get(random.nextInt(hand.size()));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MEDIUM — Búsqueda greedy con función heurística (profundidad 1)
    //
    // Para cada carta posible calcula un score heurístico y elige la mejor.
    // No explora respuestas del oponente — solo evalúa el impacto inmediato.
    // Esto es una búsqueda de profundidad 1 con evaluación heurística.
    // ═══════════════════════════════════════════════════════════════════════════

    private Card decideMediumGreedy(List<Card> hand, Game game, String botId) {
        Suit trump = game.getTrumpSuit();
        Trick trick = game.getCurrentTrick();

        Card best = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (Card card : hand) {
            double score = heuristicScore(card, trick, trump, game, botId);
            if (score > bestScore) {
                bestScore = score;
                best = card;
            }
        }

        return best != null ? best : hand.get(0);
    }

    /**
     * Función heurística h(carta, estado).
     *
     * Estima el valor de jugar una carta sin explorar el futuro.
     * Factores:
     *   + Puntos ganados si la carta gana la baza
     *   - Valor sacrificado si la carta pierde
     *   - Penalización por gastar triunfo sin ganar o innecesariamente
     *   ± Ajuste estratégico según posición (ganando/perdiendo)
     */
    private double heuristicScore(Card card, Trick trick, Suit trump,
                                  Game game, String botId) {
        double score = 0.0;
        boolean leading = trick.getPlayedCards().isEmpty();

        if (leading) {
            // ─ Liderando la baza
            // Preferimos cartas baratas no-triunfo para no arriesgar valor.
            // Si vamos perdiendo, atacamos con cartas de alto valor.
            if (card.getSuit() == trump) score -= 4.0;
            score -= card.getPoints() * 0.8;

            if (!isBotWinning(game, botId) && card.getPoints() >= 10) {
                score += 3.0; // ataque agresivo cuando se va perdiendo
            }

        } else {
            // ─ Siguiendo la baza
            int trickPointsIfWin = trick.getTotalPoints() + card.getPoints();
            boolean wins = wouldWinTrick(card, trick, trump);

            if (wins) {
                score += trickPointsIfWin * WIN_MULTIPLIER;
                score -= card.getPoints() * 0.4;         // ganar con carta barata = mejor
                if (card.getSuit() == trump) score -= 2.0; // triunfo usado para ganar es aceptable
            } else {
                score -= card.getPoints() * LOSS_MULTIPLIER; // perder carta valiosa = malo
                if (card.getSuit() == trump) score -= TRUMP_WASTE_PENALTY; // nunca perder triunfo gratis
            }
        }

        // Bonus estratégico: cuando se va ganando, conservar triunfos
        if (isBotWinning(game, botId) && card.getSuit() == trump) {
            score -= 2.0;
        }

        return score;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HARD — Minimax con poda Alpha-Beta
    //
    // Algoritmo Minimax (Russell & Norvig, cap. 5):
    //
    //   MAX node (bot)      : elige la carta que MAXIMIZA su utilidad
    //   MIN node (oponente) : elige la carta que MINIMIZA la utilidad del bot
    //
    // Poda Alpha-Beta:
    //   α = mejor garantía del MAX hasta ahora (empieza en -∞)
    //   β = mejor garantía del MIN hasta ahora (empieza en +∞)
    //   Si β ≤ α → podar (el oponente nunca permitirá esta rama)
    //
    // Información incompleta:
    //   No conocemos las cartas del oponente. Estimamos las "desconocidas"
    //   (todas las cartas del juego menos las que sí conocemos) y simulamos
    //   que el oponente elige la peor para nosotros entre ellas.
    // ═══════════════════════════════════════════════════════════════════════════

    private Card decideHardMinimax(List<Card> hand, Game game, String botId) {
        Suit trump = game.getTrumpSuit();
        Trick currentTrick = game.getCurrentTrick();
        List<Card> unknownCards = getUnknownCards(game, botId);

        Card bestCard = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        double alpha = Double.NEGATIVE_INFINITY;
        double beta  = Double.POSITIVE_INFINITY;

        for (Card card : hand) {
            // MAX node: el bot juega 'card'
            SimulatedTrick simTrick = new SimulatedTrick(currentTrick, botId, card);

            double score;
            if (simTrick.isComplete(game.getPlayers().size())) {
                // Baza completada con esta carta → evaluar resultado directo
                score = evaluateCompletedTrick(simTrick, botId, trump);
            } else {
                // Quedan jugadores → MIN node (oponente responde)
                score = minValue(simTrick, unknownCards, trump, game, botId,
                        MINIMAX_DEPTH - 1, alpha, beta);
            }

            if (score > bestScore) {
                bestScore = score;
                bestCard = card;
            }
            alpha = Math.max(alpha, bestScore);
        }

        return bestCard != null ? bestCard : hand.get(0);
    }

    /**
     * MIN node: el oponente elige la jugada que MINIMIZA la utilidad del bot.
     *
     * Como no sabemos las cartas del oponente, probamos con un subconjunto
     * representativo de las cartas "desconocidas" (las que el oponente podría tener).
     * Poda Beta: si encontramos un valor ≤ alpha, cortamos (el MAX nunca elegiría esta rama).
     */
    private double minValue(SimulatedTrick trick, List<Card> unknownCards,
                            Suit trump, Game game, String botId,
                            int depth, double alpha, double beta) {
        if (depth == 0 || unknownCards.isEmpty()) {
            return evaluatePartialTrick(trick, botId, trump, game);
        }

        double minScore = Double.POSITIVE_INFINITY;

        // Seleccionamos las cartas candidatas más representativas del oponente
        // para no explotar el árbol de búsqueda
        List<Card> candidates = selectOpponentCandidates(unknownCards, trick, trump);

        for (Card opCard : candidates) {
            SimulatedTrick newTrick = new SimulatedTrick(trick, "OPPONENT", opCard);

            double score;
            if (newTrick.isComplete(game.getPlayers().size())) {
                score = evaluateCompletedTrick(newTrick, botId, trump);
            } else {
                // Más jugadores → MAX node de nuevo (turno del bot en siguiente vuelta)
                score = maxValue(newTrick, unknownCards, trump, game, botId,
                        depth - 1, alpha, beta);
            }

            minScore = Math.min(minScore, score);
            beta = Math.min(beta, minScore);

            // ─ Poda Alpha-Beta
            // El MAX ya tiene garantizado 'alpha'. Si el MIN puede forzar
            // algo ≤ alpha, el MAX nunca elegiría esta rama → cortar.
            if (beta <= alpha) break;
        }

        return minScore;
    }

    /**
     * MAX node: el bot elige la jugada que MAXIMIZA su utilidad.
     * Se usa en los niveles intermedios del árbol cuando el bot tiene otro turno.
     */
    private double maxValue(SimulatedTrick trick, List<Card> unknownCards,
                            Suit trump, Game game, String botId,
                            int depth, double alpha, double beta) {
        if (depth == 0) {
            return evaluatePartialTrick(trick, botId, trump, game);
        }

        Player bot = game.getPlayerById(botId);
        if (bot == null) return 0;

        double maxScore = Double.NEGATIVE_INFINITY;

        for (Card card : bot.getHand()) {
            SimulatedTrick newTrick = new SimulatedTrick(trick, botId, card);
            double score = minValue(newTrick, unknownCards, trump, game, botId,
                    depth - 1, alpha, beta);

            maxScore = Math.max(maxScore, score);
            alpha = Math.max(alpha, maxScore);

            if (beta <= alpha) break; // Poda Alpha
        }

        return maxScore;
    }

    // ─ Funciones de evaluación (nodos terminales)

    /**
     * Evalúa el resultado de una baza completada.
     * Utilidad = puntos ganados (positivo) o puntos perdidos (negativo).
     */
    private double evaluateCompletedTrick(SimulatedTrick trick, String botId, Suit trump) {
        String winnerId = determineTrickWinner(trick, trump);
        int points = trick.getTotalPoints();

        if (botId.equals(winnerId)) {
            return points * WIN_MULTIPLIER;  // ganamos la baza
        } else {
            return -points * 0.8;            // perdimos la baza
        }
    }

    /**
     * Evalúa una baza aún no completada.
     * Combina: ventaja en puntuación global + quién va ganando la baza parcial.
     */
    private double evaluatePartialTrick(SimulatedTrick trick, String botId,
                                        Suit trump, Game game) {
        String currentLeader = determineTrickWinner(trick, trump);
        int trickPoints = trick.getTotalPoints();

        // Diferencia de puntuación en el juego completo
        Player bot = game.getPlayerById(botId);
        int botScore = bot != null ? bot.getScore() : 0;
        int maxOpponentScore = game.getPlayers().stream()
                .filter(p -> !p.getId().equals(botId))
                .mapToInt(Player::getScore)
                .max().orElse(0);

        double score = (botScore - maxOpponentScore) * 0.3;

        // Ajustar según quién lidera la baza parcial
        if (botId.equals(currentLeader)) {
            score += trickPoints * 0.8;
        } else {
            score -= trickPoints * 0.5;
        }

        return score;
    }

    // ─ Helpers

    /**
     * Determina si 'card' ganaría la baza actual según las reglas de Brisca.
     */
    private boolean wouldWinTrick(Card card, Trick trick, Suit trump) {
        SimulatedTrick sim = new SimulatedTrick(trick, "__CHECK__", card);
        return "__CHECK__".equals(determineTrickWinner(sim, trump));
    }

    /**
     * Determina el ganador de una baza simulada.
     * Replica las reglas de TrickResolver: triunfo > palo líder > resto.
     */
    private String determineTrickWinner(SimulatedTrick trick, Suit trump) {
        Map<String, Card> played = trick.getPlayedCards();
        Suit leadSuit = trick.getLeadSuit();

        String winnerId = null;
        Card winningCard = null;

        for (Map.Entry<String, Card> entry : played.entrySet()) {
            if (winningCard == null) {
                winnerId = entry.getKey();
                winningCard = entry.getValue();
                continue;
            }
            if (isCardStronger(entry.getValue(), winningCard, leadSuit, trump)) {
                winnerId = entry.getKey();
                winningCard = entry.getValue();
            }
        }
        return winnerId;
    }

    private boolean isCardStronger(Card challenger, Card current, Suit leadSuit, Suit trump) {
        boolean cTrump = challenger.getSuit() == trump;
        boolean wTrump = current.getSuit() == trump;
        if (cTrump && !wTrump) return true;
        if (!cTrump && wTrump) return false;
        if (cTrump) return challenger.getNumericValue() > current.getNumericValue();
        boolean cLead = challenger.getSuit() == leadSuit;
        boolean wLead = current.getSuit() == leadSuit;
        if (cLead && !wLead) return true;
        if (!cLead && wLead) return false;
        if (cLead) return challenger.getNumericValue() > current.getNumericValue();
        return false;
    }

    /**
     * Devuelve todas las cartas que el bot NO conoce: no están en su mano
     * ni han sido jugadas en la baza actual.
     * Son las posibles cartas que el oponente podría tener.
     */
    private List<Card> getUnknownCards(Game game, String botId) {
        Set<Card> knownCards = new HashSet<>();

        Player bot = game.getPlayerById(botId);
        if (bot != null) knownCards.addAll(bot.getHand());
        knownCards.addAll(game.getCurrentTrick().getPlayedCards().values());

        List<Card> allCards = new ArrayList<>();
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                allCards.add(new Card(suit, rank));
            }
        }
        allCards.removeAll(knownCards);
        return allCards;
    }

    /**
     * Selecciona hasta 5 cartas representativas del oponente para la simulación.
     * Evaluar todas las cartas desconocidas haría el árbol demasiado grande.
     * Estrategia: carta de mayor valor, menor valor, triunfo más bajo, dos intermedias.
     */
    private List<Card> selectOpponentCandidates(List<Card> unknownCards,
                                                SimulatedTrick trick, Suit trump) {
        if (unknownCards.isEmpty()) return unknownCards;

        List<Card> sorted = new ArrayList<>(unknownCards);
        sorted.sort(Comparator.comparingInt(Card::getPoints).reversed());

        Set<Card> candidates = new LinkedHashSet<>();
        candidates.add(sorted.get(0));                         // mayor valor
        candidates.add(sorted.get(sorted.size() - 1));        // menor valor

        sorted.stream()
                .filter(c -> c.getSuit() == trump)
                .min(Comparator.comparingInt(Card::getNumericValue))
                .ifPresent(candidates::add);                   // triunfo más bajo

        if (sorted.size() > 3) candidates.add(sorted.get(sorted.size() / 2));
        if (sorted.size() > 4) candidates.add(sorted.get(1));

        return new ArrayList<>(candidates);
    }

    private boolean isBotWinning(Game game, String botId) {
        Player bot = game.getPlayerById(botId);
        if (bot == null) return false;
        return game.getPlayers().stream()
                .filter(p -> !p.getId().equals(botId))
                .allMatch(p -> bot.getScore() > p.getScore());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SimulatedTrick — estado inmutable de una baza durante la simulación
    //
    // Representa una baza parcial o completa en el árbol Minimax.
    // Es inmutable: cada "jugada" crea una nueva instancia sin modificar la anterior.
    // ═══════════════════════════════════════════════════════════════════════════

    private static class SimulatedTrick {
        private final Map<String, Card> playedCards;
        private final String leadPlayerId;

        /** Crea una simulación a partir de una baza real del juego. */
        SimulatedTrick(Trick original, String playerId, Card card) {
            this.playedCards = new LinkedHashMap<>(original.getPlayedCards());
            this.playedCards.put(playerId, card);
            this.leadPlayerId = original.getLeadPlayerId() != null
                    ? original.getLeadPlayerId()
                    : playerId; // si la baza estaba vacía, este jugador lidera
        }

        /** Crea una simulación a partir de otra simulación (nodo hijo en el árbol). */
        SimulatedTrick(SimulatedTrick previous, String playerId, Card card) {
            this.playedCards = new LinkedHashMap<>(previous.playedCards);
            this.playedCards.put(playerId, card);
            this.leadPlayerId = previous.leadPlayerId;
        }

        Map<String, Card> getPlayedCards() { return playedCards; }

        Suit getLeadSuit() {
            Card lead = playedCards.get(leadPlayerId);
            return lead != null ? lead.getSuit() : null;
        }

        int getTotalPoints() {
            return playedCards.values().stream().mapToInt(Card::getPoints).sum();
        }

        boolean isComplete(int playerCount) {
            return playedCards.size() >= playerCount;
        }
    }
}