# Assignment 3 Task 2: Hangman Game Protocol

**Author:** Murtadha Ghanim  
**Date:** October 4, 2026

## How to Run

Server (default port 8888):
```bash
./gradlew Server --console=plain -q
```

Server on another port:
```bash
./gradlew Server -Pport=9000 --console=plain -q
```

Client:
```bash
./gradlew Client --console=plain -q
```

Client with host/port:
```bash
./gradlew Client -Phost=localhost -Pport=9000 --console=plain -q
```

Run automated tests:
```bash
./gradlew test
```

## Video Demonstration

**Link:** [Insert your 4-7 minute demonstration video link here]

## Implemented Features Checklist

### Required Features
- [x] Set player name
- [x] Start new game
- [x] Guess one letter
- [x] Request current state
- [x] Correct/incorrect guess handling
- [x] Duplicate-guess rejection
- [x] Win detection
- [x] Loss after six incorrect guesses
- [x] Final score
- [x] Give up
- [x] Graceful quit
- [x] Server-side validation and authoritative state
- [x] Automated JUnit tests

### Optional Features
- [ ] Hint feature
- [ ] Whole-word guessing
- [ ] Leaderboard extra credit

---

# Protocol Specification

## Overview

The client sends one JSON object at a time as a serialized Java `String`. The server returns one JSON object as a serialized Java `String`. Every successful response contains `"ok":true` and a `type`. Errors contain `"ok":false` and a human-readable `message`.

The server owns all game logic. The client never chooses the word, decides whether a guess is correct, counts misses, detects a win/loss, or calculates score. A rejected request does not change game state.

The normal game flow is:

1. `name`
2. `start`
3. zero or more `guess` / `state` requests
4. game ends by win, loss, or `giveup`
5. player may `start` another game or `quit`

---

## 1. Set Player Name

### Request
```json
{
  "type": "name",
  "name": "Mia"
}
```

Fields:
- `type` - String, must be `name`
- `name` - non-empty String

### Success Response
```json
{
  "type": "name",
  "ok": true,
  "message": "Welcome Mia! Ready to play Hangman?"
}
```

### Errors
Missing name:
```json
{
  "ok": false,
  "message": "Field 'name' does not exist in request"
}
```

Wrong type:
```json
{
  "type": "name",
  "ok": false,
  "message": "Field 'name' must be a String"
}
```

Empty name:
```json
{
  "type": "name",
  "ok": false,
  "message": "Name cannot be empty"
}
```

---

## 2. Start New Game

A player name must already be set and no other game may currently be active. The server randomly selects a word, clears previous guesses, resets incorrect guesses to zero, and prints the selected word in the server terminal for testing/grading.

### Request
```json
{
  "type": "start"
}
```

### Success Response
```json
{
  "type": "start",
  "ok": true,
  "status": "active",
  "maskedWord": "______",
  "guessedLetters": [],
  "incorrectLetters": [],
  "incorrectGuesses": 0,
  "maxIncorrectGuesses": 6,
  "hangman": "<ASCII stage 0>",
  "gameOver": false,
  "message": "New game started for Mia"
}
```

### Errors
No name:
```json
{
  "type": "start",
  "ok": false,
  "message": "Set a player name before starting a game"
}
```

Game already active:
```json
{
  "type": "start",
  "ok": false,
  "message": "A game is already active"
}
```

Both errors leave the current state unchanged.

---

## 3. Guess One Letter

`letter` must contain exactly one alphabetic character. Case is ignored. A repeated guess is rejected without changing the game.

### Request
```json
{
  "type": "guess",
  "letter": "a"
}
```

### Active-Game Success Response
```json
{
  "type": "guess",
  "ok": true,
  "letter": "a",
  "correct": true,
  "status": "active",
  "maskedWord": "_a_a",
  "guessedLetters": ["a"],
  "incorrectLetters": [],
  "incorrectGuesses": 0,
  "maxIncorrectGuesses": 6,
  "hangman": "<current ASCII stage>",
  "gameOver": false,
  "message": "Correct guess: a"
}
```

For an incorrect but non-terminal guess, `correct` is `false`, the letter is included in `incorrectLetters`, and `incorrectGuesses` increases by one.

### Win Response
When every letter is revealed:
```json
{
  "type": "guess",
  "ok": true,
  "letter": "v",
  "correct": true,
  "status": "won",
  "maskedWord": "java",
  "guessedLetters": ["a", "j", "v"],
  "incorrectLetters": [],
  "incorrectGuesses": 0,
  "maxIncorrectGuesses": 6,
  "hangman": "<current ASCII stage>",
  "gameOver": true,
  "secretWord": "java",
  "score": 6,
  "message": "You won! The word was 'java'."
}
```

Win score formula:
```text
score = 6 - incorrect guesses
```

### Loss Response
After the sixth incorrect guess:
```json
{
  "type": "guess",
  "ok": true,
  "letter": "z",
  "correct": false,
  "status": "lost",
  "maskedWord": "____",
  "incorrectGuesses": 6,
  "maxIncorrectGuesses": 6,
  "hangman": "<ASCII stage 6>",
  "gameOver": true,
  "secretWord": "java",
  "score": 0,
  "message": "You lost. The word was 'java'."
}
```

### Errors
No active game:
```json
{
  "type": "guess",
  "ok": false,
  "message": "No active game"
}
```

Missing letter:
```json
{
  "ok": false,
  "message": "Field 'letter' does not exist in request"
}
```

Wrong JSON type:
```json
{
  "type": "guess",
  "ok": false,
  "message": "Field 'letter' must be a String"
}
```

Invalid value:
```json
{
  "type": "guess",
  "ok": false,
  "message": "Field 'letter' must contain exactly one alphabetic letter"
}
```

Repeated guess:
```json
{
  "type": "guess",
  "ok": false,
  "message": "Letter 'a' was already guessed"
}
```

---

## 4. Request Current State

This request reads state without modifying it.

### Request
```json
{
  "type": "state"
}
```

### Success Response
```json
{
  "type": "state",
  "ok": true,
  "status": "active",
  "maskedWord": "_a_a",
  "guessedLetters": ["a", "z"],
  "incorrectLetters": ["z"],
  "incorrectGuesses": 1,
  "maxIncorrectGuesses": 6,
  "hangman": "<ASCII stage 1>",
  "gameOver": false,
  "message": "Current game state"
}
```

### Error
```json
{
  "type": "state",
  "ok": false,
  "message": "No active game"
}
```

---

## 5. Give Up

Giving up ends an active game and reveals the word. It is not a win or loss and therefore the response does **not** contain a score.

### Request
```json
{
  "type": "giveup"
}
```

### Success Response
```json
{
  "type": "giveup",
  "ok": true,
  "status": "given_up",
  "gameOver": true,
  "secretWord": "protocol",
  "maskedWord": "p_______",
  "guessedLetters": ["p"],
  "incorrectLetters": [],
  "incorrectGuesses": 0,
  "maxIncorrectGuesses": 6,
  "hangman": "<current ASCII stage>",
  "message": "Game abandoned. The word was 'protocol'."
}
```

### Error
```json
{
  "type": "giveup",
  "ok": false,
  "message": "No active game"
}
```

---

## 6. Quit

### Request
```json
{
  "type": "quit"
}
```

### Success Response
```json
{
  "type": "quit",
  "ok": true,
  "message": "Goodbye Mia!"
}
```

After this response the server closes that client connection.

---

# General Protocol Errors

Missing request type:
```json
{
  "ok": false,
  "message": "No request type was given"
}
```

`type` is not a String:
```json
{
  "ok": false,
  "message": "Field 'type' must be a String"
}
```

Unsupported type:
```json
{
  "ok": false,
  "message": "Type 'dance' is not supported"
}
```

Invalid/non-object JSON:
```json
{
  "ok": false,
  "message": "Request must be a valid JSON object"
}
```

---

# Example Client/Server Conversation

```text
Client -> {"type":"name","name":"Mia"}
Server -> {"type":"name","ok":true,"message":"Welcome Mia! Ready to play Hangman?"}

Client -> {"type":"start"}
Server -> {"type":"start","ok":true,"status":"active","maskedWord":"____",...}

Client -> {"type":"guess","letter":"j"}
Server -> {"type":"guess","ok":true,"correct":true,"maskedWord":"j___",...}

Client -> {"type":"guess","letter":"z"}
Server -> {"type":"guess","ok":true,"correct":false,"incorrectGuesses":1,...}

Client -> {"type":"guess","letter":"z"}
Server -> {"type":"guess","ok":false,"message":"Letter 'z' was already guessed"}

Client -> {"type":"state"}
Server -> {"type":"state","ok":true,"maskedWord":"j___","incorrectGuesses":1,...}

Client -> {"type":"giveup"}
Server -> {"type":"giveup","ok":true,"status":"given_up","secretWord":"java",...}

Client -> {"type":"quit"}
Server -> {"type":"quit","ok":true,"message":"Goodbye Mia!"}
```

---

# Validation, State Management, and Server Authority

The server validates request type, required fields, JSON value types, letter format, duplicate guesses, and whether an operation is legal in the current game state. Validation occurs before game state is modified. For example, a repeated or malformed guess is rejected before it is added to `usedLetters` or changes `misses`.

Game state is stored on the server for the duration of the client connection: player name, secret word, used letters, number of misses, and whether a game is active. Starting a new game resets only the game-specific state. The client receives a masked word and display state but never receives the secret word until a win, loss, or give-up.

The server chooses the word, decides whether a guess is correct, reveals all occurrences of correct letters, advances the Hangman stage after incorrect guesses, detects terminal state, and calculates the final score. The client only sends input and displays the JSON response.

# Robustness

The server converts malformed requests into JSON errors instead of intentionally terminating the game loop. Missing fields, wrong types, invalid letters, repeated guesses, and invalid game-state actions are rejected without changing game state.

The client checks `ok` before reading success-only fields. If the server is unavailable at connection time, the client reports that it could not connect. If communication fails after connecting, `sendRequest` reports the error and returns `null` rather than trying to interpret a missing response.

# Automated Tests

`src/test/java/HangmanServerTest.java` covers:
- successful game start
- correct guess
- incorrect guess
- duplicate guess
- request in an invalid game state
- wrong JSON field type
- win and score
- loss and zero score
- give up without a score

The tests use a fixed word (`java`) so assertions are deterministic.

# Assumptions

1. The supplied word list contains alphabetic lowercase words.
2. The starter architecture handles one connected client at a time, so the static state represents that client only.
3. Guess letters are normalized to lowercase; uppercase and lowercase are equivalent.
4. Six incorrect guesses causes a loss, matching the seven supplied Hangman stages numbered 0-6.

# Known Limitations

1. The optional leaderboard is not implemented.
2. Hint and whole-word guessing are not implemented because they are not required for the core assignment.
3. The server follows the starter design and services one client connection at a time rather than using one thread per client.
4. The demonstration video link must be added after recording the required 4-7 minute demo.
