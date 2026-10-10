import org.json.JSONArray;
import org.json.JSONObject;

import java.net.*;
import java.io.*;
import java.util.Scanner;

/**
 * Hangman Game Client.
 * This client only collects input and displays authoritative server responses.
 */
public class HangmanClient {
    static Socket sock;
    static ObjectOutputStream oos;
    static ObjectInputStream in;

    static Scanner scanner = new Scanner(System.in);
    static boolean inGame = false;
    static boolean hasName = false;
    static String playerName = "";

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Expected arguments: <host(String)> <port(int)>");
            System.exit(1);
        }

        String host = args[0];
        int port;
        try {
            port = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            System.out.println("Port must be an integer");
            return;
        }

        try {
            sock = new Socket(host, port);
            oos = new ObjectOutputStream(sock.getOutputStream());
            in = new ObjectInputStream(sock.getInputStream());

            System.out.println("╔════════════════════════════════════════╗");
            System.out.println("║     WELCOME TO HANGMAN GAME!           ║");
            System.out.println("╚════════════════════════════════════════╝");

            boolean running = true;
            while (running) {
                if (!hasName) running = showInitialMenu();
                else if (!inGame) running = showMainMenu();
                else running = showGameMenu();
                System.out.println();
            }

            closeConnection();
        } catch (ConnectException e) {
            System.out.println("Could not connect to the Hangman server.");
        } catch (Exception e) {
            System.out.println("Client error: " + e.getMessage());
        }
    }

    static boolean showInitialMenu() {
        System.out.println("────────────────────────────────────────");
        System.out.println("  1. Set Your Name");
        System.out.println("  2. Quit");
        System.out.println("────────────────────────────────────────");
        System.out.print("Enter choice: ");

        String choice = scanner.nextLine().trim();
        switch (choice) {
            case "1": setName(); return true;
            case "2": quit(); return false;
            default:
                System.out.println("Invalid choice. Please try again.");
                return true;
        }
    }

    static boolean showMainMenu() {
        System.out.println("────────────────────────────────────────");
        System.out.println("MAIN MENU:");
        System.out.println("  1. Start New Game");
        System.out.println("  2. Quit");
        System.out.println("────────────────────────────────────────");
        System.out.print("Enter choice: ");

        String choice = scanner.nextLine().trim();
        switch (choice) {
            case "1": startGame(); return true;
            case "2": quit(); return false;
            default:
                System.out.println("Invalid choice. Please try again.");
                return true;
        }
    }

    static boolean showGameMenu() {
        System.out.println("\n────────────────────────────────────────");
        System.out.println("Type one letter to guess");
        System.out.println("Or choose:");
        System.out.println("  1 - Show game state");
        System.out.println("  2 - Give up (return to main menu)");
        System.out.println("  0 - Quit game");
        System.out.println("────────────────────────────────────────");
        System.out.print("Your input: ");
        String input = scanner.nextLine().trim();

        if (input.equals("1")) {
            requestState();
            return true;
        } else if (input.equals("2")) {
            giveUp();
            return true;
        } else if (input.equals("0")) {
            quit();
            return false;
        }

        if (input.isEmpty()) {
            System.out.println("Please enter a letter or command.");
            return true;
        }

        guessLetter(input);
        return true;
    }

    static void setName() {
        System.out.print("\nEnter your name: ");
        String name = scanner.nextLine().trim();

        JSONObject request = new JSONObject();
        request.put("type", "name");
        request.put("name", name);

        JSONObject response = sendRequest(request);
        if (response == null) return;
        if (response.getBoolean("ok")) {
            hasName = true;
            playerName = name;
            System.out.println("\n" + response.getString("message"));
        } else {
            showError(response);
        }
    }

    static void startGame() {
        JSONObject request = new JSONObject();
        request.put("type", "start");

        JSONObject response = sendRequest(request);
        if (response == null) return;
        if (response.getBoolean("ok")) {
            inGame = true;
            System.out.println("\n" + response.getString("message"));
            displayState(response);
        } else {
            showError(response);
        }
    }

    static void guessLetter(String input) {
        JSONObject request = new JSONObject();
        request.put("type", "guess");
        request.put("letter", input);

        JSONObject response = sendRequest(request);
        if (response == null) return;
        if (!response.getBoolean("ok")) {
            showError(response);
            return;
        }

        System.out.println("\n" + response.getString("message"));
        displayState(response);

        if (response.optBoolean("gameOver", false)) {
            inGame = false;
            System.out.println("Secret word: " + response.getString("secretWord"));
            System.out.println("Final score: " + response.getInt("score"));
        }
    }

    static void requestState() {
        JSONObject request = new JSONObject();
        request.put("type", "state");

        JSONObject response = sendRequest(request);
        if (response == null) return;
        if (response.getBoolean("ok")) {
            displayState(response);
        } else {
            showError(response);
        }
    }

    static void giveUp() {
        System.out.print("\nAre you sure you want to give up? (yes/no): ");
        String confirm = scanner.nextLine().trim().toLowerCase();
        if (!(confirm.equals("yes") || confirm.equals("y"))) {
            System.out.println("\nContinuing game...");
            return;
        }

        JSONObject request = new JSONObject();
        request.put("type", "giveup");

        JSONObject response = sendRequest(request);
        if (response == null) return;
        if (response.getBoolean("ok")) {
            System.out.println("\n" + response.getString("message"));
            System.out.println("Secret word: " + response.getString("secretWord"));
            inGame = false;
        } else {
            showError(response);
        }
    }

    static boolean quit() {
        JSONObject request = new JSONObject();
        request.put("type", "quit");

        JSONObject response = sendRequest(request);
        if (response != null && response.getBoolean("ok")) {
            System.out.println("\n" + response.getString("message"));
            System.out.println("Thanks for playing!");
        }
        return false;
    }

    static void displayState(JSONObject response) {
        if (response.has("hangman")) {
            System.out.println(response.getString("hangman"));
        }
        if (response.has("maskedWord")) {
            System.out.println("Word: " + spaced(response.getString("maskedWord")));
        }
        if (response.has("guessedLetters")) {
            System.out.println("Guessed letters: " + join(response.getJSONArray("guessedLetters")));
        }
        if (response.has("incorrectLetters")) {
            System.out.println("Incorrect letters: " + join(response.getJSONArray("incorrectLetters")));
        }
        if (response.has("incorrectGuesses")) {
            System.out.println("Incorrect guesses: " + response.getInt("incorrectGuesses")
                    + "/" + response.getInt("maxIncorrectGuesses"));
        }
    }

    static String spaced(String masked) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < masked.length(); i++) {
            if (i > 0) out.append(' ');
            out.append(masked.charAt(i));
        }
        return out.toString();
    }

    static String join(JSONArray array) {
        if (array.length() == 0) return "(none)";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < array.length(); i++) {
            if (i > 0) out.append(", ");
            out.append(array.getString(i));
        }
        return out.toString();
    }

    static void showError(JSONObject response) {
        System.out.println("✗ Error: " + response.optString("message", "Unknown server error"));
    }

    static JSONObject sendRequest(JSONObject request) {
        try {
            oos.writeObject(request.toString());
            oos.flush();
            String res = (String) in.readObject();
            return new JSONObject(res);
        } catch (Exception e) {
            System.out.println("Error communicating with server: " + e.getMessage());
            return null;
        }
    }

    static void closeConnection() {
        try {
            if (oos != null) oos.close();
            if (in != null) in.close();
            if (sock != null) sock.close();
        } catch (Exception e) {
            // Ignore cleanup errors while exiting.
        }
    }
}
