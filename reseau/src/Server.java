import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    private static final int PORT = 1337;
    private static final String PASSWORD = "password";
    private static final ExecutorService pool = Executors.newFixedThreadPool(10);
    public static final List<ClientHandler> clients = Collections.synchronizedList(new ArrayList<>());
    private static volatile boolean isShuttingDown = false;

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

    static class CommandHandler implements Runnable {
        @Override
        public void run() {
            try (BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in))) {
                while (!isShuttingDown) {
                    String[] command = consoleReader.readLine().trim().split(" ");

                    switch (command[0]) {
                        case "list":
                            listClients();
                            break;
                        case "progress":
                            requestProgress();
                            break;
                        case "shutdown":
                            shutdownServer();
                            break;
                        case "start":
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

        private void listClients() {
            System.out.println("Connected clients:");
            synchronized (clients) {
                for (ClientHandler client : clients) {
                    System.out.println("Client ID: " + client.getClientId());
                }
            }
        }

        private void requestProgress() {
            synchronized (clients) {
                for (ClientHandler client : clients) {
                    client.requestProgress();
                }
            }
        }

        private void shutdownServer() {
            isShuttingDown = true;
            try {
                new Socket("localhost", PORT).close();  // Unblock the accept() call
            } catch (IOException e) {
                System.out.println("Error shutting down server: " + e.getMessage());
            }
        }

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

class ClientHandler extends Thread {
    private final Socket socket;
    private final String password;
    private PrintWriter writer;
    private BufferedReader reader;
    private final int clientId;
    private static int nextClientId = 1;

    public ClientHandler(Socket socket, String password) {
        this.socket = socket;
        this.password = password;
        this.clientId = nextClientId++;
    }

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
                    handleFound(response);
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
            send("OK");
        } else if ("PROGRESS".equalsIgnoreCase(response)) {
            requestProgress();
        } else {
            send("Connection rejected. Expected READY or PROGRESS from client.");
            System.out.println("Expected READY or PROGRESS from client, but received: " + response);
        }
    }

    public void requestProgress() {
        send("PROGRESS");
        try {
            receive();
        } catch (IOException e) {
            System.out.println("Error requesting progress from client " + clientId + ": " + e.getMessage());
        }
    }

    public void startMining(int difficulty, String data, int index, int startingNonce, int increment) {
        send("NONCE " + startingNonce + " " + increment);
        send("PAYLOAD " + data);
        send("SOLVE " + difficulty);
    }

    private void handleFound(String response) {
        String[] parts = response.split(" ");
        if (parts.length == 3) {
            String hash = parts[1];
            String nonce = parts[2];

            // Valider le hash localement
            boolean isValid = validateHash(1, nonce, hash);
            if (isValid) {
                send("SOLVED");
                notifyAllClients("PAUSE");
            } else {
                send("CONTINUE");
            }
        }
    }

    private boolean validateHash(int difficulty, String nonce, String hash) {
        String endpoint = "https://projet-raizo-idmc.netlify.app/.netlify/functions/validate_work";
        String token = "recPLSnso0wuEOctS";
        String requestBody = "{\"d\":" + difficulty + ",\"n\":\"" + nonce + "\",\"h\":\"" + hash + "\"}";

        try {
            URL url = new URL(endpoint);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Authorization", "Bearer " + token);
            connection.setRequestProperty("Content-Type", "application/json");

            connection.setDoOutput(true);
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = requestBody.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream(), "utf-8"));
            StringBuilder response = new StringBuilder();
            String responseLine;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }

            return response.toString().contains("code: 200");
        } catch (IOException e) {
            System.out.println("Error validating hash: " + e.getMessage());
            return false;
        }
    }

    private void notifyAllClients(String message) {
        synchronized (Server.clients) {
            for (ClientHandler client : Server.clients) {
                client.send(message);
            }
        }
    }
}
