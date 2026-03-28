# Brisca WebSocket Testing Guide

## ✅ Application is Running!
- **WebSocket Endpoint**: `ws://localhost:8089/ws`
- **HTTP Endpoint**: `http://localhost:8089`
- **Port**: 8089
- **Status**: READY FOR TESTING

## 📡 WebSocket Endpoints

### Connect to WebSocket
```javascript
const socket = new SockJS('http://localhost:8089/ws');
const stompClient = Stomp.over(socket);

stompClient.connect({}, function(frame) {
    console.log('Connected: ' + frame);
    
    // Subscribe to game updates
    stompClient.subscribe('/topic/game/GAME_123', function(message) {
        const gameState = JSON.parse(message.body);
        console.log('Game State Updated:', gameState);
    });
    
    // Subscribe to game events
    stompClient.subscribe('/topic/game/GAME_123/events', function(message) {
        const event = JSON.parse(message.body);
        console.log('Game Event:', event);
    });
});
```

## 🎮 Game Flow

### 1. Create a Game
```javascript
stompClient.send('/app/game/create', {}, JSON.stringify({
    gameId: 'GAME_123',
    minPlayers: 2,
    maxPlayers: 4
}));
```

**Response on `/topic/lobby`:**
```json
{
    "gameId": "GAME_123",
    "state": "WAITING_FOR_PLAYERS",
    "players": [],
    "currentPlayerId": null,
    "trumpCard": null,
    "remainingCards": 40
}
```

### 2. Join the Game (Player 1)
```javascript
stompClient.send('/app/game/GAME_123/join', {}, JSON.stringify({
    gameId: 'GAME_123',
    playerId: 'PLAYER_1',
    playerName: 'Alice'
}));
```

**Response on `/topic/game/GAME_123`:**
```json
{
    "gameId": "GAME_123",
    "state": "WAITING_FOR_PLAYERS",
    "players": [
        {
            "id": "PLAYER_1",
            "name": "Alice",
            "score": 0,
            "hand": [
                {"suit": "OROS", "rank": "ACE", "points": 11},
                {"suit": "COPAS", "rank": "THREE", "points": 10},
                {"suit": "ESPADAS", "rank": "KING", "points": 4}
            ],
            "handSize": 3
        }
    ],
    "currentPlayerId": "PLAYER_1"
}
```

### 3. Join the Game (Player 2)
```javascript
stompClient.send('/app/game/GAME_123/join', {}, JSON.stringify({
    gameId: 'GAME_123',
    playerId: 'PLAYER_2',
    playerName: 'Bob'
}));
```

### 4. Start the Game
```javascript
stompClient.send('/app/game/GAME_123/start', {}, JSON.stringify({
    gameId: 'GAME_123'
}));
```

**Response on `/topic/game/GAME_123`:**
```json
{
    "gameId": "GAME_123",
    "state": "IN_PROGRESS",
    "players": [...],
    "currentPlayerId": "PLAYER_1",
    "trumpCard": {"suit": "BASTOS", "rank": "SEVEN", "points": 0},
    "trumpSuit": "BASTOS",
    "remainingCards": 34,
    "currentTrick": {
        "playedCards": {},
        "leadPlayerId": null,
        "totalPoints": 0
    }
}
```

### 5. Play a Card
```javascript
stompClient.send('/app/game/GAME_123/play', {}, JSON.stringify({
    gameId: 'GAME_123',
    playerId: 'PLAYER_1',
    suit: 'OROS',
    rank: 'ACE'
}));
```

**Response on `/topic/game/GAME_123`:**
Updated game state with card played in currentTrick

**Event on `/topic/game/GAME_123/events`:**
```json
{
    "type": "CARD_PLAYED",
    "data": {
        "gameId": "GAME_123",
        "playerId": "PLAYER_1",
        "card": "As de Oros"
    },
    "timestamp": 1234567890
}
```

### 6. Get Current Game State (anytime)
```javascript
stompClient.send('/app/game/GAME_123/state', {}, JSON.stringify({}));
```

## 🃏 Card Values Reference

### Suits (Palos)
- `OROS` - Gold
- `COPAS` - Cups
- `ESPADAS` - Swords
- `BASTOS` - Clubs

### Ranks (Valores)
- `ACE` (1) - 11 points
- `THREE` (3) - 10 points
- `KING` (12) - 4 points
- `HORSE` (11) - 3 points
- `JACK` (10) - 2 points
- `TWO, FOUR, FIVE, SIX, SEVEN` - 0 points

## 🔔 Events You'll Receive

### On `/topic/game/{gameId}`
- Full game state after every action

### On `/topic/game/{gameId}/events`
- `GAME_CREATED` - Game was created
- `PLAYER_JOINED` - Player joined
- `GAME_STARTED` - Game started
- `CARD_PLAYED` - Card was played
- `TRICK_COMPLETED` - Round completed (includes winner & points)
- `GAME_FINISHED` - Game ended (includes winner)

## 🧪 Testing with Browser Console

Copy this into your browser console after including SockJS and STOMP libraries:

```javascript
// Include these libraries first:
// <script src="https://cdn.jsdelivr.net/npm/sockjs-client@1/dist/sockjs.min.js"></script>
// <script src="https://cdn.jsdelivr.net/npm/stompjs@2.3.3/lib/stomp.min.js"></script>

const socket = new SockJS('http://localhost:8089/ws');
const client = Stomp.over(socket);

client.connect({}, function() {
    console.log('✅ Connected to Brisca WebSocket');
    
    // Create and join a test game
    client.subscribe('/topic/lobby', msg => console.log('Lobby:', JSON.parse(msg.body)));
    client.subscribe('/topic/game/TEST_GAME', msg => console.log('Game State:', JSON.parse(msg.body)));
    client.subscribe('/topic/game/TEST_GAME/events', msg => console.log('Event:', JSON.parse(msg.body)));
    
    // Create game
    setTimeout(() => {
        client.send('/app/game/create', {}, JSON.stringify({
            gameId: 'TEST_GAME',
            minPlayers: 2,
            maxPlayers: 2
        }));
    }, 500);
    
    // Join as player 1
    setTimeout(() => {
        client.send('/app/game/TEST_GAME/join', {}, JSON.stringify({
            gameId: 'TEST_GAME',
            playerId: 'PLAYER_1',
            playerName: 'Alice'
        }));
    }, 1000);
    
    // Join as player 2
    setTimeout(() => {
        client.send('/app/game/TEST_GAME/join', {}, JSON.stringify({
            gameId: 'TEST_GAME',
            playerId: 'PLAYER_2',
            playerName: 'Bob'
        }));
    }, 1500);
    
    // Start game
    setTimeout(() => {
        client.send('/app/game/TEST_GAME/start', {}, JSON.stringify({
            gameId: 'TEST_GAME'
        }));
    }, 2000);
});
```

## 🌐 CORS Configuration

If your frontend is on a different port, the application is configured with:
```
setAllowedOriginPatterns("*")
```
This allows connections from any origin during development.

## 📝 Notes

- Each player only sees their own cards in the `hand` array
- Other players' hands show `handSize` but empty `hand` array
- Trump card is visible to all players
- Current player's turn is indicated by `currentPlayerId`
- Game state updates are broadcast to all subscribed clients
