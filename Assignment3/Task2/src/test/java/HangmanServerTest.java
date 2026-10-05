import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class HangmanServerTest {

    @Before
    public void setUp() {
        HangmanServer.loadGameStages();
        HangmanServer.initGame();
        // Fixed word makes correct/incorrect guesses deterministic.
        HangmanServer.WORDS = new String[]{"java"};
    }

    private JSONObject send(JSONObject req) {
        return HangmanServer.handleRawRequest(req.toString());
    }

    private void nameAndStart() {
        JSONObject name = new JSONObject();
        name.put("type", "name");
        name.put("name", "Tester");
        assertTrue(send(name).getBoolean("ok"));

        JSONObject start = new JSONObject();
        start.put("type", "start");
        assertTrue(send(start).getBoolean("ok"));
    }

    @Test
    public void startsGameSuccessfully() {
        JSONObject name = new JSONObject();
        name.put("type", "name");
        name.put("name", "Tester");
        JSONObject res = send(name);
        assertTrue(res.getBoolean("ok"));

        JSONObject start = new JSONObject();
        start.put("type", "start");
        res = send(start);

        assertTrue(res.getBoolean("ok"));
        assertEquals("start", res.getString("type"));
        assertEquals("____", res.getString("maskedWord"));
        assertEquals(0, res.getInt("incorrectGuesses"));
        assertEquals(0, res.getJSONArray("guessedLetters").length());
        assertEquals("active", res.getString("status"));
        assertTrue(HangmanServer.inGame);
    }

    @Test
    public void correctAndIncorrectGuess() {
        nameAndStart();

        JSONObject correct = new JSONObject();
        correct.put("type", "guess");
        correct.put("letter", "J");
        JSONObject res = send(correct);
        assertTrue(res.getBoolean("ok"));
        assertTrue(res.getBoolean("correct"));
        assertEquals("j___", res.getString("maskedWord"));
        assertEquals(0, res.getInt("incorrectGuesses"));

        JSONObject incorrect = new JSONObject();
        incorrect.put("type", "guess");
        incorrect.put("letter", "z");
        res = send(incorrect);
        assertTrue(res.getBoolean("ok"));
        assertFalse(res.getBoolean("correct"));
        assertEquals(1, res.getInt("incorrectGuesses"));
        assertEquals("z", res.getJSONArray("incorrectLetters").getString(0));
    }

    @Test
    public void duplicateGuessIsRejectedWithoutChangingState() {
        nameAndStart();

        JSONObject guess = new JSONObject();
        guess.put("type", "guess");
        guess.put("letter", "j");
        assertTrue(send(guess).getBoolean("ok"));

        JSONObject duplicate = send(guess);
        assertFalse(duplicate.getBoolean("ok"));
        assertEquals("Letter 'j' was already guessed", duplicate.getString("message"));
        assertEquals(0, HangmanServer.misses);
        assertEquals(1, HangmanServer.usedLetters.size());
    }

    @Test
    public void invalidGameStateIsRejected() {
        JSONObject guess = new JSONObject();
        guess.put("type", "guess");
        guess.put("letter", "a");

        JSONObject res = send(guess);
        assertFalse(res.getBoolean("ok"));
        assertEquals("No active game", res.getString("message"));
        assertFalse(HangmanServer.inGame);
        assertEquals(0, HangmanServer.usedLetters.size());
    }

    @Test
    public void wrongFieldTypeIsRejected() {
        nameAndStart();

        JSONObject guess = new JSONObject();
        guess.put("type", "guess");
        guess.put("letter", 7);
        JSONObject res = send(guess);

        assertFalse(res.getBoolean("ok"));
        assertEquals("Field 'letter' must be a String", res.getString("message"));
        assertEquals(0, HangmanServer.usedLetters.size());
        assertEquals(0, HangmanServer.misses);
    }

    @Test
    public void winningReturnsFinalScore() {
        nameAndStart();

        for (String letter : new String[]{"j", "a"}) {
            JSONObject guess = new JSONObject();
            guess.put("type", "guess");
            guess.put("letter", letter);
            JSONObject res = send(guess);
            assertFalse(res.getBoolean("gameOver"));
        }

        JSONObject finalGuess = new JSONObject();
        finalGuess.put("type", "guess");
        finalGuess.put("letter", "v");
        JSONObject res = send(finalGuess);

        assertTrue(res.getBoolean("ok"));
        assertTrue(res.getBoolean("gameOver"));
        assertEquals("won", res.getString("status"));
        assertEquals("java", res.getString("secretWord"));
        assertEquals(6, res.getInt("score"));
        assertFalse(HangmanServer.inGame);
    }

    @Test
    public void lossReturnsZeroScore() {
        nameAndStart();

        JSONObject res = null;
        for (String letter : new String[]{"b", "c", "d", "e", "f", "g"}) {
            JSONObject guess = new JSONObject();
            guess.put("type", "guess");
            guess.put("letter", letter);
            res = send(guess);
        }

        assertNotNull(res);
        assertTrue(res.getBoolean("gameOver"));
        assertEquals("lost", res.getString("status"));
        assertEquals(0, res.getInt("score"));
        assertEquals(6, res.getInt("incorrectGuesses"));
        assertFalse(HangmanServer.inGame);
    }

    @Test
    public void giveUpEndsGameWithoutScore() {
        nameAndStart();

        JSONObject request = new JSONObject();
        request.put("type", "giveup");
        JSONObject res = send(request);

        assertTrue(res.getBoolean("ok"));
        assertEquals("given_up", res.getString("status"));
        assertEquals("java", res.getString("secretWord"));
        assertFalse(res.has("score"));
        assertFalse(HangmanServer.inGame);
    }
}
