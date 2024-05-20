import java.io.*;
import java.net.*;
import java.security.SecureRandom;
import java.util.Base64;

public class Server {
    private static final int PORT = 1337;
    private static final String PASSWORD = "password";

    public static void main(String[] args) {
        System.out.println("Server is listening on port " + PORT);
        System.out.println("Generated password: " + PASSWORD);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("New client connected");

                // Create a connection thread for a client
                new ClientHandler(socket, PASSWORD).start();
            }
        } catch (IOException ex) {
            System.out.println("Server exception: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private static String generateRandomPassword() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[24]; // 192 bits of randomness
        random.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}

class ClientHandler extends Thread {
    private Socket socket;
    private String password;
    private PrintWriter writer;
    private BufferedReader reader;

    public ClientHandler(Socket socket, String password) {
        this.socket = socket;
        this.password = password;
    }

    public void run() {
        try {
            initializeStreams();
            send("WHO_ARE_YOU_?");
            whoAreYou();
        } catch (IOException ex) {
            System.out.println("Client handling exception: " + ex.getMessage());
            ex.printStackTrace();
        } finally {
            try {
                socket.close();
            } catch (IOException ex) {
                System.out.println("Error closing socket: " + ex.getMessage());
                ex.printStackTrace();
            }
        }
    }

    private void initializeStreams() throws IOException {
        writer = new PrintWriter(socket.getOutputStream(), true);
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    }

    private void send(String message) {
        writer.println(message);
        System.out.println("Sent: " + message);
    }

    private String receive() throws IOException {
        String response = reader.readLine();
        System.out.println("Received: " + response);
        return response;
    }

    private void whoAreYou() throws IOException {
        String response = receive();
        if ("ITS_ME".equalsIgnoreCase(response)) {
            send("GIMME_PASSWORD");
            gimmePassword();
        } else {
            send("Connection rejected");
        }
    }

    private void gimmePassword() throws IOException {
        String response = receive();
        if (response.startsWith("PASSWD ")) {
            String providedPassword = response.substring(7);
            System.out.println("Received password: " + providedPassword);

            if (password.equals(providedPassword)) {
                send("HELLO_YOU");
                handleClientCommunication();
            } else {
                send("YOU_DONT_FOOL_ME");
                System.out.println("Client provided wrong password");
            }
        } else {
            send("Invalid password format. Connection rejected");
            System.out.println("Invalid password format from client");
        }
    }

    private void handleClientCommunication() throws IOException {
        String response = receive();
        if ("READY".equalsIgnoreCase(response)) {
            String text;
            do {
                send("OK");
                text = receive();
            } while (!text.equalsIgnoreCase("bye"));
        } else {
            send("Connection rejected. Expected READY from client.");
            System.out.println("Expected READY from client, but received: " + response);
        }
    }
}
