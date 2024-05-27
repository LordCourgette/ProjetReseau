
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * La classe Client implémente un client simple qui se connecte à un serveur,
 * vérifie la connexion et gère les différentes commandes reçues du serveur.
 * Le client inclut également un mécanisme de minage qui peut être démarré et contrôlé par
 * par le biais des commandes du serveur.
 */
public class Client {

    private static String password = "password";
    private static volatile int current_nonce = 0;
    private static volatile int startingNonce = 0;
    private static volatile int increment = 0;
    private static volatile int difficulty = 0;
    private static volatile String status = "NONE";
    private static volatile String data = "";
    private static volatile Thread miningThread;

    /**
     * La méthode principale initialise le client, se connecte au serveur et gère les entrées provenant du serveur et de la console.
     * les entrées provenant à la fois du serveur et de la console.
     *
     * @param args les arguments de la ligne de commande (non utilisés)
     */
    public static void main(String[] args) {
        // Paramètres du serveur
        String hostname = "localhost";
        int port = 1337;

        // Le programme tente d'établir une connection avec le server 'localhost:1337'
        try (Socket socket = new Socket(hostname, port)) {
            // Initialisation des buffers d'écriture et de lecture entre le client et le serveur
            // Permet d'envoyer un message au serveur

            PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);

            // Permet de récupérer les messages quele eserveur nous envoie

            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Verify the server
            if (!verifyServer(reader, writer, password)) {
                return;
            }

            // Start a thread to listen for server commands
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

            // Wait for client commands from the console
            BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));
            String userInput;
            while ((userInput = consoleReader.readLine()) != null) {
                writer.println(userInput);
                if ("bye".equalsIgnoreCase(userInput)) {
                    break;
                }
            }

            // Close the connection when the user types "bye"
            System.out.println("Connection closed");
        } catch (IOException ex) {
            System.out.println("Error: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    /**
     * Vérifie le serveur en suivant un protocole d'échange.
     *
     * @param reader the BufferedReader to read from the server (lecteur de mémoire tampon à lire sur le serveur)
     * @param writer le PrintWriter pour écrire sur le serveur
     * @param password le mot de passe pour s'authentifier auprès du serveur
     * @return true si le serveur est vérifié avec succès, false sinon
     * @throws IOException en cas d'erreur d'E/S
     */
    private static boolean verifyServer(BufferedReader reader, PrintWriter writer, String password) throws IOException {
        // Le client lit WHO_ARE_YOU
        String serverMessage = reader.readLine();
        System.out.println("Server: " + serverMessage);
        if (!"WHO_ARE_YOU_?".equalsIgnoreCase(serverMessage)) {
            System.out.println("Unexpected response, closing connection");
            return false;
        }

        // Le client envoie ITS_ME
        writer.println("ITS_ME");
        System.out.println("Client: ITS_ME");

        // Le client lit GIMME_PASSWORD
        serverMessage = reader.readLine();
        System.out.println("Server: " + serverMessage);
        if (!"GIMME_PASSWORD".equalsIgnoreCase(serverMessage)) {
            System.out.println("Unexpected response, closing connection");
            return false;
        }

        // Le client envoie password au serveur
        writer.println("PASSWD " + password);
        System.out.println("Client: PASSWD " + password);

        // Le client lit HELLO_YOU
        serverMessage = reader.readLine();
        System.out.println("Server: " + serverMessage);
        if (!"HELLO_YOU".equalsIgnoreCase(serverMessage)) {
            System.out.println("Connection rejected by server. Closing connection.");
            return false;
        }

        // Le client envoie READY au serveur
        writer.println("READY");
        System.out.println("Client: READY");

        // Le client attend le message 'OK' de la part du serveur
        serverMessage = reader.readLine();
        System.out.println("Server: " + serverMessage);

        if (!"OK".equalsIgnoreCase(serverMessage)) {
            System.out.println("Expected OK from server, but received: " + serverMessage);
            return false;
        }

        return true;
    }

    /**
     * Gère les commandes reçues du serveur.
     *
     * @param command la commande reçue du serveur
     * @param writer le PrintWriter pour écrire les réponses au serveur
     */
    private static void handleServerCommand(String command, PrintWriter writer) {
        String[] splitCommand = command.split(" ");
        // Le serveur souhaite connaître le status du client
        if ("PROGRESS".equalsIgnoreCase(splitCommand[0])) {
            sendProgress(writer);
        }
        // Le serveur indique la valeur de départ du nonce ainsi que son incrément
        if ("NONCE".equalsIgnoreCase(splitCommand[0])) {
            startingNonce = Integer.parseInt(splitCommand[1]);
            increment = Integer.parseInt(splitCommand[2]);
        }
        // Le serveur indique les données qui seront concaténés au nonce avant hashage
        if ("PAYLOAD".equalsIgnoreCase(splitCommand[0])) {
            data = splitCommand[1];
        }
        // Le serveur indique la difficulté avec laquelle il faut résoudre ce problème
        // et lance la recherche du hash
        if ("SOLVE".equalsIgnoreCase(splitCommand[0])) {
            difficulty = Integer.parseInt(splitCommand[1]);
            miningThread = new Thread(() -> mine(writer, data, difficulty, increment, startingNonce));
            miningThread.start();
        }
        // Le serveur indique qu'une solution a été trouvé et stop les opérations de minage
        // de tout les clients
        if ("SOLVED".equalsIgnoreCase(splitCommand[0])) {
            miningThread.interrupt();
        }
        // Le serveur indique que la réponse envoyée par un autre client n'était pas la bonne
        // et que par conséquent le procesus de minage peut reprendre
        if ("CONTINUE".equalsIgnoreCase(splitCommand[0])) {
            miningThread.resume();
        }
    }

    /**
     * Envoie l'état d'avancement au serveur.
     *
     * @param writer the PrintWriter to write the progress status to the server
     */
    private static void sendProgress(PrintWriter writer) {
        writer.println(status);
        System.out.println("Client: " + status);
    }

    /**
     * Mines les données pour trouver un hachage qui répond à la difficulté spécifiée.
     *
     * @param writer le PrintWriter pour envoyer les résultats au serveur
     * @param data les données à extraire
     * @param difficulty le niveau de difficulté du minage
     * @param increment la valeur d'incrémentation du nonce
     * @param startingNonce la valeur initiale du nonce
     */
    private static void mine(PrintWriter writer, String data, int difficulty, int increment, int startingNonce) {
        System.out.println("Start mining");
        String targetStart = new String(new char[difficulty]).replace('\0', '0');
        int nonce = startingNonce;
        byte[] data_matrix = data.getBytes();
        
        while (true) { 
            String data_String = data + nonce;
            byte[] nonce_matrix = intToByteArray(nonce);
            byte[] addition = concatenateByteArrays(data_matrix, nonce_matrix);
            String hash = calculateHash(data_String);
            // String hash = calculateHash(addition);
            
            status = "TESTING " + Integer.toHexString(nonce);
            writer.println("FOUND " + hash + " " + Integer.toHexString(nonce));
            miningThread.suspend();
            
            nonce += increment;
        }
    }

    /**
     * Concatenes deux tableau d'octets
     *
     * @param array1 premier tableau d'octet
     * @param array2 second tableau d'octet
     * @return le tableau d'octets concaténés
     */
    public static byte[] concatenateByteArrays(byte[] array1, byte[] array2) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            outputStream.write(array1);
            outputStream.write(array2);
            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Calcule le hachage SHA-256 de l'entrée donnée.
     *
     * @param input la chaîne d'entrée à hacher
     * @return Le le hash SHA-256 de l'entrée
     */
    public static String calculateHash(String input) {
        try {
            // Intialisation du hasheur
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            // Génération du hash sous forme d'octet
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            // Formattage du résultat obtenu au format hexadecimal
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Convertit un entier en un tableau d'octets.
     *
     * @param number le nombre entier à convertir
     * @return le tableau d'octets représentant l'entier
     */
    public static byte[] intToByteArray(int number) {
        byte[] result = new byte[4]; // Un int représente 4 octets en Java
        
        // Utiliser le décalage de bits pour extraire chaque octet de l'entier
        result[0] = (byte) (number >> 24);
        result[1] = (byte) (number >> 16);
        result[2] = (byte) (number >> 8);
        result[3] = (byte) number;
        
        return result;
    }
}
