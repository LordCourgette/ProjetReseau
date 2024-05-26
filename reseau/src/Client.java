import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.security.MessageDigest;

public class Client {

    private static String password = "password";
    private static volatile int current_nonce = 0;
    private static volatile int startingNonce = 0;
    private static volatile int increment = 0;
    private static volatile int difficulty = 0;
    private static volatile String status = "NONE";
    private static volatile String data = "";
    private static volatile Thread miningThread;

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

            // Démarrer un thread pour écouter les commandes du serveur
            Thread serverListenerThread = new Thread(() -> {
                try {
                    String serverMessage;
                    while ((serverMessage = reader.readLine()) != null) {
                        System.out.println("Server: " + serverMessage);
                        handleServerCommand(serverMessage, writer);
                    }
                } catch (IOException e) {
                    System.out.println("Error reading from server: " + e.getMessage());
                    e.printStackTrace();
                }
            });
            serverListenerThread.start();

            // Attendre les commandes du client depuis la console
            BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));
            String userInput;
            while ((userInput = consoleReader.readLine()) != null) {
                writer.println(userInput);
                if ("bye".equalsIgnoreCase(userInput)) {
                    break;
                }
            }

            // Fermer la connexion lorsque l'utilisateur saisit "bye"
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

        // Lire GIMME_PASSWORD
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

    private static void handleServerCommand(String command, PrintWriter writer) {
        String[] splitCommand = command.split(" ");
        if ("PROGRESS".equalsIgnoreCase(splitCommand[0])) {
            sendProgress(writer);
        }
        if ("NONCE".equalsIgnoreCase(splitCommand[0])) {
            startingNonce = Integer.parseInt(splitCommand[1]);
            increment = Integer.parseInt(splitCommand[2]);
        }
        if ("PAYLOAD".equalsIgnoreCase(splitCommand[0])) {
            data = splitCommand[1];
        }
        if("SOLVE".equalsIgnoreCase(splitCommand[0])) {
            difficulty = Integer.parseInt(splitCommand[1]);
            miningThread = new Thread(() -> mine(writer, data, difficulty, increment, startingNonce));
            miningThread.start();
        }
        if("SOLVED".equalsIgnoreCase(splitCommand[0])) {
            miningThread.interrupt();
        }
        if("CONTINUE".equalsIgnoreCase(splitCommand[0])) {
            miningThread.resume();
        }
        // Ajouter ici d'autres commandes du serveur si nécessaire
    }

    private static void sendProgress(PrintWriter writer) {
        writer.println(status);
        System.out.println("Client: " + status);
    }

    private static void mine(PrintWriter writer, String data, int difficulty, int increment, int startingNonce) {
        System.out.println("Start mining");
        String targetStart = new String(new char[difficulty]).replace('\0', '0');
        int nonce = startingNonce;
        byte[] data_matrix = data.getBytes();

        while (true) { 
            byte[] nonce_matrix = intToByteArray(nonce);
            byte[] addition = concatenateByteArrays(data_matrix, nonce_matrix);
            String hash = calculateHash(addition);
            
            if (hash.startsWith(targetStart) && hash.charAt(difficulty) != '0') {
                writer.println("FOUND " + hash + " " + Integer.toHexString(nonce));
                miningThread.suspend();
            }
            
            nonce += increment;
        }
    }

    public static byte[] concatenateByteArrays(byte[] array1, byte[] array2) {
        // Calculer la taille du nouveau tableau
        int newArrayLength = array1.length + array2.length;
        
        // Créer un nouveau tableau pour stocker les deux tableaux concaténés
        byte[] concatenatedArray = new byte[newArrayLength];
        
        // Copier les éléments du premier tableau
        System.arraycopy(array1, 0, concatenatedArray, 0, array1.length);
        
        // Copier les éléments du deuxième tableau à partir de la fin du premier tableau
        System.arraycopy(array2, 0, concatenatedArray, array1.length, array2.length);
        
        return concatenatedArray;
    }

    public static String calculateHash(byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                hexString.append(hex.length() == 1 ? "0" : "").append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] intToByteArray(int number) {
        byte[] result = new byte[4]; // Un int fait 4 octets en Java
        
        // Utilisation des opérations de décalage de bits pour extraire chaque octet de l'entier
        result[0] = (byte) (number >> 24);
        result[1] = (byte) (number >> 16);
        result[2] = (byte) (number >> 8);
        result[3] = (byte) number;
        
        return result;
    }
}
