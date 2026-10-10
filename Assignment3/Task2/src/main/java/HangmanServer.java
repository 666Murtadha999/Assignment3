import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.*;
import java.io.*;
import java.util.*;

/**
 * Hangman Game Server.
 * The server is authoritative: all validation and game state live here.
 */
public class HangmanServer {
    static Socket sock;
    static ObjectOutputStream os;
    static ObjectInputStream in;
    static int port = 8888;

    // Per-connection game state.
    static String playerName = null;
    static String secretWord = null;
    static Set<Character> usedLetters = new HashSet<>();
    static int misses = 0;
    static boolean inGame = false;

    static final int MAX_MISSES = 6;
    static final Random RANDOM = new Random();

    // 7 stages: index 0 through 6.
    static String[] GAME_STAGES = new String[7];
    static String[] WORDS;

    public static void main(String args[]) {
        if (args.length != 1) {
            System.out.println("Expected arguments: <port(int)>");
            System.exit(1);
        }

        try {
            port = Integer.parseInt(args[0]);
        } catch (NumberFormatException nfe) {
            System.out.println("Port must be an integer");
            System.exit(2);
        }

        loadGameStages();
        loadWords();

        try (ServerSocket serv = new ServerSocket(port)) {
            System.out.println("Hangman Server ready for connections on port " + port);

            while (true) {
                System.out.println("Server waiting for a connection");
                sock = serv.accept();
                System.out.println("Client connected");

                in = new ObjectInputStream(sock.getInputStream());
                os = new ObjectOutputStream(sock.getOutputStream());
                initGame();

                boolean connected = true;
                while (connected) {
                    String raw;
                    try {
                        raw = (String) in.readObject();
                    } catch (Exception e) {
                        System.out.println("Client disconnect");
                        break;
                    }

                    JSONObject res = handleRawRequest(raw);
                    sendResponse(res);

                    if (res.optBoolean("ok", false)
                            && "quit".equals(res.optString("type"))) {
                        connected = false;
                    }
                }
                closeConnection();
            }
        } catch (Exception e) {
            e.printStackTrace();
            closeConnection();
        }
    }

    /** Parse one raw request and route it. Used by the socket loop and tests. */
    static JSONObject handleRawRequest(String raw) {
        final JSONObject req;
        try {
            req = new JSONObject(raw);
        } catch (JSONException e) {
            return error(null, "Request must be a valid JSON object");
        }
        return processRequest(req);
    }

    /** Route one parsed protocol request. */
    static JSONObject processRequest(JSONObject req) {
        if (!req.has("type")) {
            return noType(req);
        }
        if (!(req.get("type") instanceof String)) {
            return error(null, "Field 'type' must be a String");
        }

        String type = req.getString("type");
        switch (type) {
            case "name":
                return handleName(req);
            case "start":
                return handleStart(req);
            case "guess":
                return handleGuess(req);
            case "state":
                return handleState(req);
            case "giveup":
                return handleGiveUp(req);
            case "quit":
                return handleQuit(req);
            default:
                return wrongType(req);
        }
    }

    static JSONObject handleName(JSONObject req) {
        System.out.println("Name request: " + req);
        JSONObject check = testField(req, "name");
        if (!check.getBoolean("ok")) return check;
        if (!(req.get("name") instanceof String)) {
            return error("name", "Field 'name' must be a String");
        }

        String name = req.getString("name").trim();
        if (name.isEmpty()) {
            return error("name", "Name cannot be empty");
        }

        playerName = name;
        JSONObject res = success("name");
        res.put("message", "Welcome " + playerName + "! Ready to play Hangman?");
        return res;
    }

    static JSONObject handleStart(JSONObject req) {
        System.out.println("Start request: " + req);
        if (playerName == null) {
            return error("start", "Set a player name before starting a game");
        }
        if (inGame) {
            return error("start", "A game is already active");
        }
        if (WORDS == null || WORDS.length == 0) {
            loadWords();
        }

        secretWord = WORDS[RANDOM.nextInt(WORDS.length)].toLowerCase();
        usedLetters = new HashSet<>();
        misses = 0;
        inGame = true;

        System.out.println("Selected word: " + secretWord);

        JSONObject res = gameStateResponse("start");
        res.put("message", "New game started for " + playerName);
        return res;
    }

    static JSONObject handleGuess(JSONObject req) {
        System.out.println("Guess request: " + req);
        if (!inGame) {
            return error("guess", "No active game");
        }

        JSONObject check = testField(req, "letter");
        if (!check.getBoolean("ok")) return check;
        if (!(req.get("letter") instanceof String)) {
            return error("guess", "Field 'letter' must be a String");
        }

        String value = req.getString("letter");
        if (value.length() != 1 || !Character.isLetter(value.charAt(0))) {
            return error("guess", "Field 'letter' must contain exactly one alphabetic letter");
        }

        char letter = Character.toLowerCase(value.charAt(0));
        if (usedLetters.contains(letter)) {
            return error("guess", "Letter '" + letter + "' was already guessed");
        }

        // State changes only after every validation has succeeded.
        usedLetters.add(letter);
        boolean correct = secretWord.indexOf(letter) >= 0;
        if (!correct) {
            misses++;
        }

        boolean won = isWon();
        boolean lost = misses >= MAX_MISSES;

        JSONObject res = gameStateResponse("guess");
        res.put("letter", String.valueOf(letter));
        res.put("correct", correct);

        if (won || lost) {
            res.put("gameOver", true);
            res.put("status", won ? "won" : "lost");
            res.put("secretWord", secretWord);
            int score = won ? MAX_MISSES - misses : 0;
            res.put("score", score);
            res.put("message", won
                    ? "You won! The word was '" + secretWord + "'."
                    : "You lost. The word was '" + secretWord + "'.");
            inGame = false;
        } else {
            res.put("message", correct
                    ? "Correct guess: " + letter
                    : "Incorrect guess: " + letter);
        }

        return res;
    }

    static JSONObject handleState(JSONObject req) {
        System.out.println("State request: " + req);
        if (!inGame) {
            return error("state", "No active game");
        }
        JSONObject res = gameStateResponse("state");
        res.put("message", "Current game state");
        return res;
    }

    static JSONObject handleGiveUp(JSONObject req) {
        System.out.println("Give-up request: " + req);
        if (!inGame) {
            return error("giveup", "No active game");
        }

        JSONObject res = gameStateResponse("giveup");
        res.put("gameOver", true);
        res.put("status", "given_up");
        res.put("secretWord", secretWord);
        res.put("message", "Game abandoned. The word was '" + secretWord + "'.");
        // Deliberately no score: a given-up game is not a win or loss.
        inGame = false;
        return res;
    }

    static JSONObject handleQuit(JSONObject req) {
        System.out.println("Quit request: " + req);
        JSONObject res = success("quit");
        res.put("message", "Goodbye " + (playerName != null ? playerName : "player") + "!");
        return res;
    }

    /** Build the complete state the client needs to render the game. */
    static JSONObject gameStateResponse(String type) {
        if (GAME_STAGES[0] == null) loadGameStages();

        JSONObject res = success(type);
        res.put("maskedWord", maskedWord());
        res.put("guessedLetters", toJsonArray(sortedUsedLetters()));
        res.put("incorrectLetters", toJsonArray(sortedIncorrectLetters()));
        res.put("incorrectGuesses", misses);
        res.put("maxIncorrectGuesses", MAX_MISSES);
        res.put("hangman", GAME_STAGES[Math.min(misses, MAX_MISSES)]);
        res.put("gameOver", false);
        res.put("status", "active");
        return res;
    }

    static String maskedWord() {
        if (secretWord == null) return "";
        StringBuilder masked = new StringBuilder();
        for (char c : secretWord.toCharArray()) {
            masked.append(usedLetters.contains(Character.toLowerCase(c)) ? c : '_');
        }
        return masked.toString();
    }

    static boolean isWon() {
        if (secretWord == null) return false;
        for (char c : secretWord.toCharArray()) {
            if (Character.isLetter(c) && !usedLetters.contains(Character.toLowerCase(c))) {
                return false;
            }
        }
        return true;
    }

    static List<Character> sortedUsedLetters() {
        List<Character> list = new ArrayList<>(usedLetters);
        Collections.sort(list);
        return list;
    }

    static List<Character> sortedIncorrectLetters() {
        List<Character> list = new ArrayList<>();
        for (char c : usedLetters) {
            if (secretWord == null || secretWord.indexOf(c) < 0) {
                list.add(c);
            }
        }
        Collections.sort(list);
        return list;
    }

    static JSONArray toJsonArray(List<Character> letters) {
        JSONArray array = new JSONArray();
        for (char c : letters) array.put(String.valueOf(c));
        return array;
    }

    static void initGame() {
        playerName = null;
        secretWord = null;
        usedLetters = new HashSet<>();
        misses = 0;
        inGame = false;
    }

    static JSONObject testField(JSONObject req, String key) {
        if (!req.has(key)) {
            return error(null, "Field '" + key + "' does not exist in request");
        }
        return new JSONObject().put("ok", true);
    }

    static JSONObject success(String type) {
        JSONObject res = new JSONObject();
        res.put("type", type);
        res.put("ok", true);
        return res;
    }

    static JSONObject error(String type, String message) {
        JSONObject res = new JSONObject();
        if (type != null) res.put("type", type);
        res.put("ok", false);
        res.put("message", message);
        return res;
    }

    static JSONObject noType(JSONObject req) {
        System.out.println("No type request: " + req);
        return error(null, "No request type was given");
    }

    static JSONObject wrongType(JSONObject req) {
        System.out.println("Wrong type request: " + req);
        return error(null, "Type '" + req.getString("type") + "' is not supported");
    }

    static void loadGameStages() {
        try {
            InputStream is = HangmanServer.class.getResourceAsStream("/game_stages.txt");
            if (is == null) throw new IOException("game_stages.txt not found in resources");

            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line;
            StringBuilder currentStage = new StringBuilder();
            int stageIndex = 0;

            while ((line = reader.readLine()) != null) {
                if (line.equals("---")) {
                    if (stageIndex < GAME_STAGES.length) {
                        GAME_STAGES[stageIndex++] = "\n" + currentStage;
                    }
                    currentStage = new StringBuilder();
                } else if (!line.startsWith("STAGE")) {
                    currentStage.append(line).append("\n");
                }
            }
            if (currentStage.length() > 0 && stageIndex < GAME_STAGES.length) {
                GAME_STAGES[stageIndex++] = "\n" + currentStage;
            }
            reader.close();
            System.out.println("Loaded " + stageIndex + " game stages");
        } catch (Exception e) {
            throw new RuntimeException("Error loading game stages: " + e.getMessage(), e);
        }
    }

    static void loadWords() {
        try {
            WORDS = loadWordList("/words.txt");
            System.out.println("Loaded " + WORDS.length + " words");
        } catch (Exception e) {
            throw new RuntimeException("Error loading word list: " + e.getMessage(), e);
        }
    }

    static String[] loadWordList(String filename) throws IOException {
        InputStream is = HangmanServer.class.getResourceAsStream(filename);
        if (is == null) throw new IOException("Word list file not found: " + filename);

        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        List<String> words = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (!line.isEmpty()) words.add(line.toLowerCase());
        }
        reader.close();
        return words.toArray(new String[0]);
    }

    static void sendResponse(JSONObject res) {
        try {
            os.writeObject(res.toString());
            os.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static void closeConnection() {
        try {
            if (os != null) os.close();
            if (in != null) in.close();
            if (sock != null) sock.close();
        } catch (Exception e) {
            // Connection may already be closed; nothing else to do.
        } finally {
            os = null;
            in = null;
            sock = null;
        }
    }
}
