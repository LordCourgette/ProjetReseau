
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * La classe Serveur implémente un serveur simple qui écoute les connexions des clients, gère l'authentification des clients et gère les tâches minières réparties entre les clients,
 * gère l'authentification du client et les tâches de minage réparties entre les clients.
 */
public class Server {
    private static final int PORT = 1337;
    private static final String PASSWORD = "password";
    private static final ExecutorService pool = Executors.newFixedThreadPool(20);
    public static final List<ClientHandler> clients = Collections.synchronizedList(new ArrayList<>());
    private static volatile boolean isShuttingDown = false;

    /**
     * La méthode principale initialise le serveur, démarre le thread du gestionnaire de commandes,
     * et écoute les connexions des clients.
     *
     * @param args les arguments de la ligne de commande (non utilisés)
     */
    public static void main(String[] args) {
        System.out.println("Server is listening on PORT : " + PORT);
        System.out.println("Generated password : " + PASSWORD);

        // Start the command handler thread
        Thread commandThread = new Thread(new CommandHandler());
        commandThread.start();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (!isShuttingDown) {
                Socket socket = serverSocket.accept();
                if (!isShuttingDown) {
                    System.out.println("New client connected");
                    ClientHandler clientHandler = new ClientHandler(socket, PASSWORD);
                    clients.add(clientHandler);
                    pool.execute(clientHandler);
                } else {
                    socket.close();
                }
            }
        } catch (IOException e) {
            System.out.println("Server exception: " + e.getMessage());
        } finally {
            pool.shutdown();
            System.out.println("Server shutting down...");
        }
    }

    /**
     * La classe CommandHandler gère les commandes du serveur à partir de la console.
     */
    static class CommandHandler implements Runnable {
        @Override
        public void run() {
            try (BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in))) {
                while (!isShuttingDown) {
                    String[] command = consoleReader.readLine().trim().split(" ");

                    switch (command[0]) {
                        case "LIST":
                            listClients();
                            break;
                        case "PROGRESS":
                            requestProgress();
                            break;
                        case "SHUTDOWN":
                            shutdownServer();
                            break;
                        case "START":
                            int difficulty = Integer.parseInt(command[1]);
                            startMining(difficulty);
                            break;
                        default:
                            System.out.println("Unknown command: " + command[0]);
                            break;
                    }
                }
            } catch (IOException e) {
                System.out.println("Command handler exception: " + e.getMessage());
            }
        }

        /**
         * Liste tous les clients connectés.
         */
        private void listClients() {
            System.out.println("Connected clients:");
            synchronized (clients) {
                for (ClientHandler client : clients) {
                    System.out.println("Client ID: " + client.getClientId());
                }
            }
        }

        /**
         * Demande l'état d'avancement à tous les clients connectés.
         */
        private void requestProgress() {
            synchronized (clients) {
                for (ClientHandler client : clients) {
                    client.requestProgress();
                }
            }
        }

        /**
         * Arrête le serveur.
         */
        private void shutdownServer() {
            isShuttingDown = true;
            try {
                new Socket("localhost", PORT).close();  // Unblock the accept() call
            } catch (IOException e) {
                System.out.println("Error shutting down server: " + e.getMessage());
            }
        }

        /**
         * Démarre le processus d'exploitation minière avec la difficulté spécifiée.
         *
         * @param difficulté le niveau de difficulté de l'exploitation minière
         */
        private void startMining(int difficulty) {
            String data = requestServer(difficulty);
            int startingNonce = 0;
            synchronized (clients) {
                for (int i = 0; i < clients.size(); i++) {
                    ClientHandler currentClient = clients.get(i);
                    currentClient.startMining(difficulty, data, i, startingNonce, clients.size());
                    startingNonce++;
                }
            }
        }

        /**
         * Demande des données au serveur pour qu'elles soient exploitées par les clients.
         *
         * @param difficulty le niveau de difficulté pour le minage
         * @return les données à extraire
         */
        private static String requestServer(int difficulty) {
            String endpoint = "https://projet-raizo-idmc.netlify.app/.netlify/functions/generate_work?d=" + difficulty;
            String token = "recPLSnso0wuEOctS";

            try {
                URL url = new URL(endpoint);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                connection.setRequestMethod("GET");
                connection.setRequestProperty("Authorization", "Bearer " + token);
                connection.setRequestProperty("Content-Type", "application/json");

                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();
                connection.disconnect();

                return extractDataFromResponse(response.toString());
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
                e.printStackTrace();
                return null;
            }
        }

        /**
         * Extrait le champ « data » de la réponse JSON.
         *
         * @param jsonResponse la réponse JSON en tant que chaîne de caractères
         * @return la valeur du champ « data ».
         */
        private static String extractDataFromResponse(String jsonResponse) {
            String trimmedResponse = jsonResponse.trim();

            if (!trimmedResponse.startsWith("{") || !trimmedResponse.endsWith("}")) {
                System.out.println("La réponse n'est pas un objet JSON valide");
                return null;
            }

            String jsonContent = trimmedResponse.substring(1, trimmedResponse.length() - 1);
            String[] keyValuePairs = jsonContent.split(",");

            for (String pair : keyValuePairs) {
                String[] keyValue = pair.split(":", 2);

                if (keyValue.length == 2 && keyValue[0].trim().equals("\"data\"")) {
                    return keyValue[1].trim().replaceAll("\"", "");
                }
            }

            System.out.println("La clé 'data' n'a pas été trouvée dans la réponse JSON");
            return null;
        }
    }
}

/**
 * La classe ClientHandler gère la communication avec un client connecté.
 */
class ClientHandler extends Thread {
    private final Socket socket;
    private final String password;
    private PrintWriter writer;
    private BufferedReader reader;
    private final int clientId;
    private static int nextClientId = 1;

    /**
     * Construit un ClientHandler avec le socket et le mot de passe donnés.
     *
     * @param socket le socket du client
     * @param password le mot de passe pour l'authentification
     */
    public ClientHandler(Socket socket, String password) {
        this.socket = socket;
        this.password = password;
        this.clientId = nextClientId++;
    }

    /**
     * Renvoie l'identifiant du client.
     *
     * @return the client ID
     */
    public int getClientId() {
        return clientId;
    }

    @Override
    public void run() {
        try {
            initializeStreams();
            send("WHO_ARE_YOU_?");
            whoAreYou();
            while (true) {
                if (socket.isClosed()) break;
                String response = receive();
                if (response.startsWith("FOUND")) {
                    handleFound(response, 9);
                }
            }
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

    /**
     * Initialise les flux d'entrée et de sortie pour la communication.
     *
     * @throws IOException si une erreur d'entrée/sortie se produit
     */
    private void initializeStreams() throws IOException {
        writer = new PrintWriter(socket.getOutputStream(), true);
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    }

    /**
     * Envoie un message au client.
     *
     * @param message le message à envoyer
     */
    private void send(String message) {
        writer.println(message);
        System.out.println("Sent: " + message);
    }

    /**
     * Reçoit un message du client.
     *
     * @return le message reçu du client
     * @throws IOException si une erreur d'entrée/sortie se produit
     */
    private String receive() throws IOException {
        String response = reader.readLine();
        System.out.println("Received: " + response);
        return response;
    }

    /**
     * Gère l'étape d'authentification WHO_ARE_YOU_ ?.
     *
     * @throws IOException si une erreur d'entrée/sortie se produit
     */
    private void whoAreYou() throws IOException {
        String response = receive();
        if ("ITS_ME".equalsIgnoreCase(response)) {
            send("GIMME_PASSWORD");
            gimmePassword();
        } else {
            send("Connection rejected");
        }
    }

    /**
     * Gère l'étape d'authentification GIMME_PASSWORD.
     *
     * @throws IOException si une erreur d'entrée/sortie se produit
     */
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

    /**
     * Gère la communication avec le client après l'authentification.
     *
     * @throws IOException si une erreur d'entrée/sortie se produit
     */
    private void handleClientCommunication() throws IOException {
        String response = receive();
        if ("READY".equalsIgnoreCase(response)) {
            send("OK");
        } else if ("PROGRESS".equalsIgnoreCase(response)) {
            requestProgress();
        } else {
            send("Connection rejected. Expected READY or PROGRESS from client.");
            System.out.println("Expected READY or PROGRESS from client, but received: " + response);
        }
    }

    /**
     * Demande l'état d'avancement au client.
     */
    public void requestProgress() {
        send("PROGRESS");
        try {
            receive();
        } catch (IOException e) {
            System.out.println("Error requesting progress from client " + clientId + ": " + e.getMessage());
        }
    }

    /**
     * Démarre le processus de minage sur le client avec les paramètres donnés.
     *
     * @param difficulty le niveau de difficulté du minage
     * @param data les données à extraire
     * @param index l'index du client dans la liste
     * @param startingNonce le nonce de départ pour l'extraction
     * @param increment la valeur d'incrémentation du nonce
     */
    public void startMining(int difficulty, String data, int index, int startingNonce, int increment) {
        send("NONCE " + startingNonce + " " + increment);
        send("PAYLOAD " + data);
        send("SOLVE " + difficulty);
    }

    /**
     * Traite le message FOUND du client.
     *
     * @param response la réponse reçue du client
     * @param difficulty le niveau de difficulté du minage
     */
    private void handleFound(String response, int difficulty) {
        String[] parts = response.split(" ");
        if (parts.length == 3) {
            String hash = parts[1];
            String nonce = parts[2];

            // Demande la validation du hash
            boolean isValid = validateHash(difficulty, nonce, hash);
            if (isValid) {
                // S'il est valid, les clients s'arrête  
                send("SOLVED");
                notifyAllClients("PAUSE");
            } else {
                // Sinon il reprenne l'execution en cours
                send("CONTINUE");
            }
        }
    }

    /**
     * Valide le hachage reçu du client.
     *
     * @param difficulty le niveau de difficulté de l'exploitation minière
     * @param nonce le nonce utilisé pour générer le hachage
     * @param hash le hash à valider
     * @return vrai si il est valide, faux sinon
     */
    public Boolean validateHash(int difficulty, String nonce, String hash) {
        // Paramètres de construction d'une requête vers le serveur distant de vérification
        String endpoint = "https://projet-raizo-idmc.netlify.app/.netlify/functions/validate_work";
        String token = "recPLSnso0wuEOctS";
        String requestBody = "{\"d\":" + difficulty + ",\"n\":\"" + nonce + "\",\"h\":\"" + hash + "\"}";

        try {
            // Création de l'URL vers laquelle pointe la requête
            URL url = new URL(endpoint);
            // Création de la requête vers cette URL
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            // Configuration de la requête
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Authorization", "Bearer " + token);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = requestBody.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            // Récupération du code HTTP de la réponse obtenu de l'API distante
            int responseCode = connection.getResponseCode();

            // Récupération et formattage de la réponse obtenu
            StringBuilder response;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(
                    (responseCode == HttpURLConnection.HTTP_OK) ? connection.getInputStream() : connection.getErrorStream(), 
                    StandardCharsets.UTF_8))) {
                response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
            }
            
            // Traitement de la réponse afin de voir les erreurs de hashage
            String response_String = response.toString();
            // Récupère le hash attendu pour chaque mauvaises réponses
            String first_hash = extractFirstHash(response_String);
            // Détermine s'il résoud le processus de minage à la difficulté courrante
            // et l'indique clairement dans le terminal
            String targetStart = new String(new char[difficulty]).replace('\0', '0');
            if(first_hash.startsWith(targetStart) && first_hash.charAt(difficulty) != '0') {
                System.out.println("BONNE REPONSE !!!!!!!!!");
                System.out.println("BONNE REPONSE !!!!!!!!!");
                System.out.println("BONNE REPONSE !!!!!!!!!");
                System.out.println("BONNE REPONSE !!!!!!!!!");
                System.out.println("BONNE REPONSE !!!!!!!!!");
                System.out.println("BONNE REPONSE !!!!!!!!!");
                System.out.println("BONNE REPONSE !!!!!!!!!");
                System.out.println(response_String);
            }
            return false;
        } catch (IOException e) {
            System.out.println("Error validating hash: " + e.getMessage());
            return null;
        }
    }

    /**
     * Extrait le premier hash de la réponse du serveur.
     *
     * @param response la réponse du serveur sous forme de chaîne de caractères
     * @return le hash extrait
     */
    public String extractFirstHash(String response) {
        if (response == null) return null;
        Pattern pattern = Pattern.compile("Computed hash doesn't match provided hash \\(([a-fA-F0-9]+) !=");
        Matcher matcher = pattern.matcher(response);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * Notifie tous les clients avec un message donné.
     *
     * @param message le message à envoyer à tous les clients
     */
    private void notifyAllClients(String message) {
        synchronized (Server.clients) {
            for (ClientHandler client : Server.clients) {
                client.send(message);
            }
        }
    }
}
