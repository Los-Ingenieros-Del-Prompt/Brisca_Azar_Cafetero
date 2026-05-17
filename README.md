# Azar Cafetero - Brisca Game Engine

Welcome to the **Brisca Game Service** repository for Azar Cafetero. This Spring Boot microservice is the authoritative, backend engine dedicated entirely to executing the logic of the traditional Spanish card game *Brisca*. It ensures that games are fair, rules are strictly enforced, and state is synchronized accurately across all players.

## 🚀 Technology Stack

- **[Java & Spring Boot](https://spring.io/projects/spring-boot)**: Provides a robust framework for managing complex, concurrent game state machines in memory.
- **[Maven](https://maven.apache.org/)**: Dependency and build lifecycle management.
- **[Docker](https://www.docker.com/)**: Containerized for isolated, predictable deployments.
- **SonarQube**: Integrated for continuous static code analysis.

## 🛠️ Architecture & Responsibilities

This service holds the absolute "source of truth" for every active Brisca match. Clients only render what this engine tells them to render.

### 1. Core Game Mechanics
- **Deck Management**: Implements the standard 40-card Spanish deck (Oros, Copas, Espadas, Bastos). Handles cryptographically secure shuffling and dealing.
- **The "Pinta" (Trump)**: Automatically determines and tracks the trump suit for each match.
- **Ruleset & Scoring Validation**: Validates every card played. Calculates trick winners based on suit, rank, and the trump card. Automatically tallies points (As = 11, Tres = 10, Rey = 4, Caballo = 3, Sota = 2).
- **Turn State Machine**: Strictly enforces whose turn it is, ignoring invalid moves from players attempting to play out of turn.

### 2. Bot Artificial Intelligence
- Contains custom heuristic or rule-based AI logic to act as simulated opponents. The AI calculates optimal moves based on the current trick, the trump suit, and the cards in its hand, providing a challenging offline or fill-in experience.

### 3. Match Lifecycle & Cleanup
- Initializes the table state upon receiving a start signal from the Lobby Service.
- Evaluates win/loss conditions at the end of the deck.
- Triggers table cleanup routines to ensure memory is freed and "ghost" tables are removed from the system when all players abandon a match.

## 🏃‍♂️ Getting Started

### Prerequisites
- Java 17+ (JDK)
- Maven 3.8+

### Running Locally

Start the game engine locally:

```bash
./mvnw spring-boot:run
```

### Docker Deployment

```bash
docker build -t azarcafetero-brisca .
docker run -p 8083:8083 azarcafetero-brisca
```

## 🧪 Testing

Given the immense complexity of Brisca's rules—handling edge cases, ties, and specific card interactions—comprehensive unit testing is critical. The test suite aggressively validates the scoring algorithms and the turn state machine.

Run the test suite:
```bash
./mvnw test
```