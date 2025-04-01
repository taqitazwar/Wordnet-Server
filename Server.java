import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class Server {
    private static ServerSocket serverSocket;
    private static Socket clientSocket;
    private static BufferedReader in;
    private static PrintWriter out;
    
    // Buffer for multi-line messages; will be sent with the EOR marker
    private static String output = "";
    private static final String EOR = "[EOR]";  // End-of-response marker
    
    // Flag to track if greeting has been sent
    private static boolean greeted = false;

    public static void main(String[] args) {
        try {
            setup();
            authenticate();
            startGame();
        } catch (IOException e) {
            toConsole("Error: " + e);
        } finally {
            disconnect();
        }
    }

    // Setup the server socket, accept connection, and log the connection details.
    private static void setup() throws IOException {
        serverSocket = new ServerSocket(0); // use an ephemeral port
        toConsole("Server started on port: " + serverSocket.getLocalPort());
        
        clientSocket = serverSocket.accept();
        toConsole("Accepted connection from " + clientSocket.getInetAddress() +
                  " at port " + clientSocket.getPort());
        
        in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        out = new PrintWriter(clientSocket.getOutputStream(), true);
    }

    /**
     * Authenticate the user (username: "Sammy", password: "WOOF") with up to 5 total attempts.
     * 
     * 1) We ask for the correct username in a loop, up to 5 tries. 
     *    - On the first try, show the greeting ("Welcome... Enter username:").
     *    - If incorrect, show ("Username not recognised. Enter username:").
     * 
     * 2) If username is correct, ask for the password in another loop, continuing the same attempt counter.
     *    - On the first password try, show ("Enter password:").
     *    - If incorrect, show ("Password not recognised. Enter password:").
     * 
     * If the correct username and password are provided, we proceed to the game.
     * Otherwise, if we reach 5 total attempts, we disconnect.
     */
    private static void authenticate() throws IOException {
        int attempts = 0;
        
        // --- PHASE 1: Obtain correct username ---
        boolean usernameCorrect = false;
        boolean firstUsernamePrompt = true;
        while (attempts < 5 && !usernameCorrect) {
            if (firstUsernamePrompt) {
                // First attempt for username
                if (!greeted) {
                    appendOutput("Welcome to Wordnet!\nEnter username:");
                    greeted = true;
                } else {
                    // If we ever came back here, just in case
                    appendOutput("Enter username:");
                }
                firstUsernamePrompt = false;
            } else {
                // Subsequent attempts after a wrong username
                appendOutput("Username not recognised.\nEnter username:");
            }
            sendOutput();

            String username = readNonEmptyLine();
            if (username == null) {
                disconnect();
            }
            username = username.trim();
            toConsole("Username received: '" + username + "' (length: " + username.length() + ")");

            if ("Sammy".equals(username)) {
                usernameCorrect = true;  // Move on to password
            } else {
                attempts++;
            }
        }

        // If we couldn't get the correct username in < 5 tries, disconnect.
        if (!usernameCorrect) {
            appendOutput("Too many failed attempts. Goodbye!");
            sendOutput();
            disconnect();
        }

        // --- PHASE 2: Obtain correct password ---
        boolean passwordCorrect = false;
        boolean firstPasswordPrompt = true;
        while (attempts < 5 && !passwordCorrect) {
            if (firstPasswordPrompt) {
                // First attempt for password
                appendOutput("Enter password:");
                firstPasswordPrompt = false;
            } else {
                // Subsequent attempts after a wrong password
                appendOutput("Password not recognised.\nEnter password:");
            }
            sendOutput();

            String password = readNonEmptyLine();
            if (password == null) {
                disconnect();
            }
            password = password.trim();
            toConsole("Password received: '" + password + "' (length: " + password.length() + ")");

            if ("WOOF".equals(password)) {
                passwordCorrect = true;
            } else {
                attempts++;
            }
        }

        // If we couldn't get the correct password in < 5 tries, disconnect.
        if (!passwordCorrect) {
            appendOutput("Too many failed attempts. Goodbye!");
            sendOutput();
            disconnect();
        }

        // If both username and password are correct:
        appendOutput("Welcome Sammy!\nGuess the mystery six-letter word");
        sendOutput();
    }

    // The guess-the-word game; the mystery word is "TOPPLE".
    private static void startGame() throws IOException {
        final String mysteryWord = "TOPPLE";
        int turns = 0;
        while (true) {
            String guess = readNonEmptyLine();
            if (guess == null) {
                break;
            }
            guess = guess.trim();
            toConsole("Guess received: '" + guess + "' (length: " + guess.length() + ")");
            
            // Validate that the guess is exactly 6 letters and only contains alphabetical characters.
            if (!isValidGuess(guess)) {
                appendOutput("Oops! You need enter a six letter word containing only alphabetical characters and no spaces.\nTry again:");
                sendOutput();
                continue;
            }
            
            turns++;
            // If the guess matches "TOPPLE" (ignoring case), win the game.
            if (guess.equalsIgnoreCase(mysteryWord)) {
                appendOutput("You got it in " + turns + " turns - well done and goodbye!");
                sendOutput();
                break;
            } else {
                // Build the response showing correctly guessed letters and asterisks for wrong ones.
                StringBuilder response = new StringBuilder();
                for (int i = 0; i < mysteryWord.length(); i++) {
                    char guessChar = Character.toUpperCase(guess.charAt(i));
                    char targetChar = mysteryWord.charAt(i);
                    response.append(guessChar == targetChar ? targetChar : "*");
                }
                appendOutput(response.toString());
                appendOutput("Try again:");
                sendOutput();
            }
        }
    }

    // Returns true if the guess is exactly 6 letters and all are alphabetical.
    private static boolean isValidGuess(String guess) {
        if (guess.length() != 6) {
            return false;
        }
        for (char c : guess.toCharArray()) {
            if (!Character.isLetter(c)) {
                return false;
            }
        }
        return true;
    }

    // Reads a non-empty line from the client (skipping accidental blank lines).
    private static String readNonEmptyLine() throws IOException {
        String line;
        while ((line = in.readLine()) != null) {
            if (!line.trim().isEmpty()) {
                return line;
            }
        }
        return null;
    }

    // Appends a line to the output buffer (CR is used for line breaks).
    private static void appendOutput(String line) {
        output += line + "\r";
    }

    // Sends the buffered output (with the [EOR] marker) to the client and clears the buffer.
    private static void sendOutput() {
        out.println(output + EOR);
        out.flush();
        output = "";
    }

    // Logs a message to the server console.
    private static void toConsole(String message) {
        System.out.println(message);
    }

    // Closes streams and sockets, then exits.
    private static void disconnect() {
        try {
            if (out != null) { out.close(); }
            if (in != null) { in.close(); }
            if (clientSocket != null && !clientSocket.isClosed()) { clientSocket.close(); }
            if (serverSocket != null && !serverSocket.isClosed()) { serverSocket.close(); }
            toConsole("Disconnected.");
        } catch (IOException ioex) {
            toConsole("Error during disconnect: " + ioex);
        }
        System.exit(0);
    }
}
