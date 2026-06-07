import com.fasterxml.jackson.core.JsonProcessingException;
import model.enums.ManagerActions;
import model.enums.RiskLevel;
import model.enums.Sender;
import java.io.*;
import java.net.Socket;
import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import serializables.Game;
import serializables.Message;
import serializables.WorkerGame;

import java.nio.file.Files;

public class Manager {
    private final Scanner scanner;
    Properties prop = new Properties();

    public Manager(Scanner scanner) {
        this.scanner = scanner;
    }
    static void showActionsForManager() {
        System.out.println("\n========== Manager Menu ==========");
        System.out.println("1. Add new game/games");
        System.out.println("2. Remove a game");
        System.out.println("3. Reestablish deleted game");
        System.out.println("4. Modify an existing game");
        System.out.println("5. Show total profit/loss per game");
        System.out.println("6. Show profit/loss per player");
        System.out.println("7. Show profit/loss for a provider's games");
        System.out.println("8. Exit manager menu");
        System.out.println("==================================");
    }

    public void start() {
        while (true) {
            showActionsForManager();
            System.out.print("Select an option: ");

            if (!scanner.hasNextInt()) {
                System.out.println("Invalid input. Please enter a number.");
                scanner.nextLine();
                continue;
            }

            int choice = scanner.nextInt();
            scanner.nextLine();

            switch (choice) {
                case 1:
                    addNewGameFromJson(scanner);
                    break;
                case 2:
                    removeGame(scanner);
                    break;
                case 3:
                    reEstablishGame(scanner);
                    break;
                case 4:
                    modifyGame(scanner);
                    break;
                case 5:
                    showProfitPerGame(scanner);
                    break;
                case 6:
                    showProfitPerPlayer(scanner);
                    break;
                case 7:
                    showProfitPerProvider(scanner);
                    break;
                case 8:
                    System.out.println("Exiting manager menu...");
                    return;
                default:
                    System.out.println("Unknown option.");
            }
        }
    }

    private void addNewGameFromJson(Scanner scanner) {
        System.out.println("\n========== Add Games From JSON Folder ==========");

        // Base folder που περιέχει game1/, game2/, ... φακέλους
        File gamesBaseFolder = new File("games");

        if (!gamesBaseFolder.exists() || !gamesBaseFolder.isDirectory()) {
            System.out.println("Games folder not found at: " + gamesBaseFolder.getAbsolutePath());
            return;
        }

        List<CreateGameRequest> requests = new ArrayList<>();
        int gameIndex = 1;

        // Loop με μεταβλητή τερματισμού - όσο υπάρχει ο επόμενος φάκελος game(N)
        while (true) {
            File gameFolder = new File(gamesBaseFolder, "game" + gameIndex);

            // Condition τερματισμού: δεν υπάρχει άλλος φάκελος
            if (!gameFolder.exists() || !gameFolder.isDirectory()) {
                break;
            }

            try {
                CreateGameRequest request = buildRequestFromFolder(gameFolder, gameIndex);
                if (request != null) {
                    requests.add(request);
                    System.out.println("Loaded game from " + gameFolder.getName()
                            + ": " + request.getGame().getName());
                }
            } catch (Exception e) {
                System.out.println("Failed to load " + gameFolder.getName() + ": " + e.getMessage());
            }

            gameIndex++;
        }

        if (requests.isEmpty()) {
            System.out.println("No games found to send.");
            return;
        }

        System.out.println("\nFound " + requests.size() + " games. Sending to Master...\n");

        // Αποστολή κάθε request μέσω serializables.Message
        int i = 0;
        while (i < requests.size()) {
            CreateGameRequest req = requests.get(i);
            Message<CreateGameRequest> message = new Message<>(Sender.MANAGER, ManagerActions.CREATE_GAME, req, null);

            Object response = sendMessageToMaster(message);
            System.out.println("[" + req.getGame().getName() + "] -> " + response);

            i++;
        }

        System.out.println("\nAll games processed.");
    }

    private void removeGame(Scanner scanner){
        System.out.println("\n========== Delete serializables.Game ==========");
        System.out.print("Enter serializables.Game Name to Delete: ");
        String gameName = scanner.nextLine();

        Message<String> message = new Message<>(Sender.MANAGER, ManagerActions.DELETE_GAME, gameName, null);
        Object response = sendMessageToMaster(message);
        System.out.println("Response from server on game deletion: " + response);

    }

    private void reEstablishGame(Scanner scanner) {
        System.out.println("\n========== Reestablish Deleted serializables.Game ==========");
        System.out.print("Enter serializables.Game Name to reestablish: ");
        String gameName = scanner.nextLine();

        Message<String> message = new Message<>(Sender.MANAGER, ManagerActions.REESTABLISH_GAME, gameName, null);
        Object response = sendMessageToMaster(message);
        System.out.println("Response from server on game deletion: " + response);

    }

    private void modifyGame(Scanner scanner) {
        System.out.println("\n========== Modify serializables.Game ==========");
        System.out.print("Enter serializables.Game Name to Modify: ");
        String gameName = scanner.nextLine().trim();

        Float minBet = promptMinBet(scanner);
        Float maxBet = promptMaxBet(scanner, minBet);
        RiskLevel riskLevel = promptRiskLevel(scanner);

        ModifyGameRequest modifyRequest = new ModifyGameRequest(gameName, minBet, maxBet, riskLevel);
        Message<ModifyGameRequest> message = new Message<>(Sender.MANAGER, ManagerActions.MODIFY_GAME, modifyRequest, null);
        String response = (String) sendMessageToMaster(message);
        System.out.println(response);
    }

    private void showProfitPerGame(Scanner scanner) {
        System.out.println("\n========== Show Profit Per serializables.Game ==========");
        System.out.print("Enter serializables.Game Name to Show Profits: ");
        String gameName = scanner.nextLine();

        Message message = new Message(Sender.MANAGER, ManagerActions.PNL_PER_GAME, gameName, null);
        Object response = sendMessageToMaster(message);

        if (response instanceof String) {
            System.out.println(response);
        } else {
            float profit = (float) response;
            System.out.println(profit);
        }

    }

    private void showProfitPerProvider(Scanner scanner) {

        System.out.println("\n========== Show Profit Per Provider ==========");
        System.out.print("Enter Provider Name to Show Profits: ");
        String providerName = scanner.nextLine();

        // Create UUID for map-reduce action
        String requestId = UUID.randomUUID().toString();

        Message message = new Message(Sender.MANAGER, ManagerActions.PNL_PER_PROVIDER, providerName, requestId);
        Object response = sendMessageToMaster(message);

        if (response instanceof String) {
            System.out.println(response);
        } else {
            Collection<WorkerGame> providerGames = (Collection<WorkerGame>) response;
            for (WorkerGame wg : providerGames) {
                System.out.println("- " + wg.getGame().getName() + wg.getTotalProfits());
            }
        }

    }

    private void showProfitPerPlayer(Scanner scanner){
        System.out.println("\n========== Show Profit Per Player ==========");
        System.out.print("Enter Player Name to Show Profits: ");
        String playerName = scanner.nextLine();

        // Create UUID for map-reduce action
        String requestId = UUID.randomUUID().toString();

        Message message = new Message<>(Sender.MANAGER, ManagerActions.PNL_PER_PLAYER, playerName, requestId);
        Object response = sendMessageToMaster(message);

        if (response instanceof String) {
            System.out.println(response);
        } else {
            float profits = 0;
            Collection<WorkerGame> gamesPlayerHasPlayed = (Collection<WorkerGame>) response;
            for (WorkerGame wg : gamesPlayerHasPlayed) {
                profits += wg.getPlayerProfits(playerName);
            }
            System.out.println("Total Profit/Loss: " + profits);
        }
    }

    private Object sendMessageToMaster(Message<?> message) {
        try {
            FileInputStream fis = new FileInputStream("src/config.properties");
            prop.load(fis);

            String masterIp = prop.getProperty("master.ip");
            int masterPort = Integer.parseInt(prop.getProperty("master.port"));

            // Socket Initialization
            Socket socket = new Socket(masterIp, masterPort);
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            out.writeObject(message);
            out.flush();

            return in.readObject();

        } catch (IOException | ClassNotFoundException e) {
            return "Could not communicate with Master: " + e.getMessage();
        }
    }

    // -- Helper Methods
    private Float promptMinBet(Scanner scanner) {
        Float minBet = null;
        while (true) {
            System.out.print("Enter Min Bet: ");
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) break;
            try {
                float parsed = Float.parseFloat(input);
                if (parsed < 0.1f) {
                    System.out.println("Invalid input. Min Bet must be at least 0.1.");
                } else {
                    minBet = parsed;
                    break;
                }
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a valid number.");
            }
        }
        return minBet;
    }

    private Float promptMaxBet(Scanner scanner, Float minBet) {
        Float maxBet = null;
        while (true) {
            System.out.print("Enter Max Bet: ");
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) break;
            try {
                float parsed = Float.parseFloat(input);
                if (parsed < 0.1f) {
                    System.out.println("Invalid input. Max Bet must be at least 0.1.");
                } else if (minBet != null && parsed <= minBet) {
                    System.out.println("Invalid input. Max Bet must be greater than Min Bet (" + minBet + ").");
                } else {
                    maxBet = parsed;
                    break;
                }
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a valid number.");
            }
        }
        return maxBet;
    }

    private RiskLevel promptRiskLevel(Scanner scanner) {
        while (true) {
            System.out.print("Enter Risk Level (low, medium, high): ");
            String input = scanner.nextLine().trim().toUpperCase();

            try {
                return RiskLevel.valueOf(input);
            } catch (IllegalArgumentException e) {
                System.out.println("Invalid input. Risk Level must be low, medium, or high.");
            }
        }
    }

    /**
     * Διαβάζει έναν φάκελο game(N), βρίσκει το .json αρχείο,
     * φορτώνει το PNG βάσει του GameLogo path και γυρνάει ένα CreateGameRequest.
     */
    private CreateGameRequest buildRequestFromFolder(File gameFolder, int gameIndex) throws IOException {
        // 1. Βρίσκουμε το JSON αρχείο μέσα στον φάκελο
        File[] jsonFiles = gameFolder.listFiles((dir, fileName) -> fileName.toLowerCase().endsWith(".json"));

        if (jsonFiles == null || jsonFiles.length == 0) {
            System.out.println("No JSON file found in " + gameFolder.getName());
            return null;
        }

        File jsonFile = jsonFiles[0];

        // 2. Parse JSON με JsonNode (manual, δεν χρειαζόμαστε DTO)
        JsonNode root = JsonUtil.MAPPER.readTree(jsonFile);

        String gameName      = root.get("GameName").asText();
        String providerName  = root.get("ProviderName").asText();
        int stars            = root.get("Stars").asInt();
        int votes            = root.get("NoOfVotes").asInt();
        String logoPath      = root.get("GameLogo").asText();
        float minBet         = (float) root.get("MinBet").asDouble();
        float maxBet         = (float) root.get("MaxBet").asDouble();
        RiskLevel riskLevel  = RiskLevel.valueOf(root.get("RiskLevel").asText().toUpperCase());
        String hashKey       = root.get("HashKey").asText();

        // 3. Φορτώνουμε το PNG από το path του JSON
        // Το path στο JSON είναι τύπου "/games/game1/game1.png" - strip το leading "/"
        // για να γίνει relative στο project root
        String cleanPath = logoPath.startsWith("/") ? logoPath.substring(1) : logoPath;
        File logoFile = new File(cleanPath);

        byte[] logoBytes;
        if (logoFile.exists()) {
            logoBytes = Files.readAllBytes(logoFile.toPath());
        } else {
            System.out.println("Logo file not found at " + cleanPath + " for " + gameName
                    + " - sending without logo.");
            logoBytes = null;
        }

        // 4. Φτιάχνουμε το serializables.Game object (με gamelogo=null αφού το serializables.Game κρατάει bytes
        // μόνο στον Worker - εδώ στο request στέλνουμε τα bytes ξεχωριστά)
        Game game = new Game(gameName, providerName, stars, votes, null,
                minBet, maxBet, riskLevel, hashKey);

        // 5. Συσκευάζουμε σε CreateGameRequest
        return new CreateGameRequest(game, logoBytes);
    }

    // Deprecated
    private void addNewGame(Scanner scanner) {
        System.out.println("1 -> Enter game features manually");
        System.out.println("2 -> Enter game features from json ");

        int choice = scanner.nextInt();
        scanner.nextLine();

        switch (choice) {
            case 1:
                addNewGameManually(scanner);
                break;
            case 2:
                addNewGameFromJson(scanner);
                break;
            default:
                System.out.println("Unknown option");
        }
    }
    private void addNewGameManually(Scanner scanner) {
        System.out.println("\n========== Create New serializables.Game ==========");
        System.out.print("Enter serializables.Game Name: ");
        String gameName = scanner.nextLine();
        System.out.print("Enter Provider Name (providerID): ");
        String providerName = scanner.nextLine();
        int stars;
        System.out.print("Enter Stars (1-5): ");
        stars = 0;
        while (stars < 1 || stars > 5) {
            try {
                stars = Integer.parseInt(scanner.nextLine());
                if (stars < 1 || stars > 5)
                    System.out.println("Invalid input. Please enter stars (1-5): ");
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a number (1-5): ");
            }
        }
        System.out.print("Enter Number of Votes: ");
        int votes = Integer.parseInt(scanner.nextLine());
        //System.out.print("Enter serializables.Game Logo: ");
        //String gameLogo = scanner.nextLine();

        Float minBet = promptMinBet(scanner);
        Float maxBet = promptMaxBet(scanner, minBet);
        RiskLevel riskLevel = promptRiskLevel(scanner);

        System.out.print("Enter Hash Key: ");
        String hashKey = scanner.nextLine();

        Game game = new Game(gameName, providerName, stars, votes, null, minBet, maxBet, riskLevel, hashKey);

        String json;
        try {
            // Convert game object to json (string)
            json = JsonUtil.MAPPER.writeValueAsString(game);

            // Create serializables.Message
            Message<String> message = new Message<>(Sender.MANAGER, ManagerActions.CREATE_GAME, json, null);

            // Replies for create game will be of type String
            String response = (String) sendMessageToMaster(message);
            System.out.println(response);

        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

}
