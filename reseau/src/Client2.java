import java.io.*;
import java.net.*;

public class Client2 {

    private static String password = "password";

    public static void main(String[] args) {
        String hostname = "localhost";
        int port = 1337;

        try (Socket socket = new Socket(hostname, port)) {
            PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Vérifier le serveur
            if (!verifyServer(reader, writer, password)) {
                return;
            }

            // fonction temporaire pour attendre la suite
            BufferedReader userInputReader = new BufferedReader(new InputStreamReader(System.in));
            String userInput;
            while ((userInput = userInputReader.readLine()) != null) {
                writer.println(userInput);
                if ("bye".equalsIgnoreCase(userInput)) {
                    break;
                }
                String serverResponse = reader.readLine();
                System.out.println("Server: " + serverResponse);
            }

            System.out.println("Connection closed");
        } catch (IOException ex) {
            System.out.println("Error: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private static boolean verifyServer(BufferedReader reader, PrintWriter writer, String password) throws IOException {
        // Lire WHO_ARE_YOU
        String serverMessage = reader.readLine();
        System.out.println("Server: " + serverMessage);

        // Envoyer ITS_ME
        if (!"WHO_ARE_YOU_?".equalsIgnoreCase(serverMessage)) {
            System.out.println("Unexpected response, closing connection");
            return false;
        }
        writer.println("ITS_ME");
        System.out.println("Client: ITS_ME");

        // Lire Gimme Password
        serverMessage = reader.readLine();
        System.out.println("Server: " + serverMessage);

        // Envoyer password to server
        if (!"GIMME_PASSWORD".equalsIgnoreCase(serverMessage)) {
            System.out.println("Unexpected response, closing connection");
            return false;
        }
        writer.println("PASSWD " + password);
        System.out.println("Client: PASSWD " + password);

        // Lire HELLO_YOU
        serverMessage = reader.readLine();
        System.out.println("Server: " + serverMessage);

        if (!"HELLO_YOU".equalsIgnoreCase(serverMessage)) {
            System.out.println("Connection rejected by server. Closing connection.");
            return false;
        }

        // Envoyer READY to server
        writer.println("READY");
        System.out.println("Client: READY");

        // Attendre OK du serveur
        serverMessage = reader.readLine();
        System.out.println("Server: " + serverMessage);

        if (!"OK".equalsIgnoreCase(serverMessage)) {
            System.out.println("Expected OK from server, but received: " + serverMessage);
            return false;
        }

        return true;
    }
}
