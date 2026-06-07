import model.enums.*;
import serializables.*;

import java.io.*;
import java.net.Socket;
import java.util.*;

public class Player {

    // Balance is set to 100 whenever user is connected
    private float balance;
    private final Scanner scanner;
    Properties prop = new Properties();

    public Player(Scanner scanner) {
        this.balance = 100f;
        this.scanner = scanner;
    }

    public void start() {
        System.out.println("Welcome! Your starting balance is: " + balance);

        while (true) {
            showActionsForPlayer();
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
                    showAvailableGames();
                    break;
                case 2:
                    searchGames(scanner);
                    break;
                case 3:
                    playGame(scanner);
                    break;
                case 4:
                    rateGame(scanner);
                    break;
                case 5:
                    addBalance(scanner);
                    break;
                case 6:
                    System.out.println("Exiting player menu...");
                    return;
                default:
                    System.out.println("Unknown option.");
            }
        }
    }

    static void showActionsForPlayer() {
        System.out.println("\n========== Player Menu ==========");
        System.out.println("1. Show available games");
        System.out.println("2. Search/Filter available games");
        System.out.println("3. Play a game");
        System.out.println("4. Rate a game");
        System.out.println("5. Add balance");
        System.out.println("6. Exit player menu");
        System.out.println("=================================");
    }

    public void showAvailableGames() {
        System.out.println("\n========== Show Available Games ==========");

        String requestId = UUID.randomUUID().toString();
        Message message = new Message(Sender.PLAYER, PlayerActions.SHOW_AVAILABLE_GAMES, null, requestId);
        Object response = sendMessageToMaster(message);

        if (response instanceof String) {
            System.out.println(response);
            return;
        }

        Collection<WorkerGame> availableGames = (Collection<WorkerGame>) response;

        if (availableGames.isEmpty()) {
            System.out.println("No games available at the moment.");
            return;
        }

        int index = 1;
        for (WorkerGame wg : availableGames) {
            Game g = wg.getGame();

            // Star display
            String starDisplay = buildStarDisplay(g.getStars());

            // Average rating from player votes
            float avgRating = wg.getAverageRating();
            String ratingDisplay = wg.getRatingCount() > 0
                    ? String.format("%.1f/5 (%d votes)", avgRating, wg.getRatingCount())
                    : "No ratings yet";

            System.out.println("┌─────────────────────────────────────────────┐");
            System.out.printf(" │  #%-3d  %-35s  │%n", index, g.getName());
            System.out.println("├─────────────────────────────────────────────┤");
            System.out.printf("│  Provider:    %-29s │%n", g.getProvider());
            System.out.printf("│  Stars:       %-29s │%n", starDisplay);
            System.out.printf("│  Rating:      %-29s │%n", ratingDisplay);
            System.out.printf("│  Bet Range:   %-5.2f - %-22.2f  │%n", g.getMinBet(), g.getMaxBet());
            System.out.printf("│  Category:    %-29s │%n", wg.getBetCategory());
            System.out.printf("│  Risk Level:  %-29s │%n", g.getRiskLevel());
            System.out.printf("│  Jackpot:     %-29s │%n", String.format("%.1fx", wg.getJackpot()));
            System.out.println("└─────────────────────────────────────────────┘");

            index++;
        }

        System.out.println("\nTotal games available: " + availableGames.size());
    }

    private String buildStarDisplay(int stars) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            sb.append(i < stars ? "★" : "☆");
        }
        return sb.toString();
    }

    public void searchGames(Scanner scanner) {
        System.out.println("\n========== Search Games ==========");

        Integer minStars = getMinStars(scanner);
        BetCategory betCategory = getBetCategory(scanner);
        RiskLevel riskLevel = getRiskLevel(scanner);

        // Create UUID for map-reduce action
        String requestId = UUID.randomUUID().toString();

        // Create serializables.SearchFilters Object to use as payload
        SearchFilters filters = new SearchFilters(minStars, betCategory, riskLevel, requestId);

        // Wrap in message
        Message<SearchFilters> message = new Message<>(Sender.PLAYER, PlayerActions.FILTER_GAMES, filters, requestId);

        Object response = sendMessageToMaster(message);
        Collection<WorkerGame> availableGames = (Collection<WorkerGame>) response;

        System.out.println("Available Games for these Filters");
        for (WorkerGame wg : availableGames) {
            System.out.println("- " + wg.getGame().getName());
        }
    }

    public void playGame(Scanner scanner) {
        System.out.println("\n========== Play serializables.Game ==========");

        System.out.println("Enter Player ID: ");
        String playerId = scanner.nextLine().trim();

        System.out.println("Enter serializables.Game Name: ");
        String gameName = scanner.nextLine().trim();

        float betAmount;
        while (true) {
            betAmount = getBetAmount(scanner);

            // Local check of balance
            if (betAmount > balance) {
                System.out.println("Insufficient balance! You have " + balance + ".");
                System.out.println("1. Enter a different bet amount");
                System.out.println("2. Add balance");
                System.out.println("3. Cancel");
                System.out.print("Choose: ");
                String choice = scanner.nextLine().trim();
                if (choice.equals("2")) {
                    addBalance(scanner);
                } else if (choice.equals("3")) {
                    return;
                }
                continue;
            }
            break;
        }

        // Τοπική αφαίρεση bet πριν σταλεί το request
        balance -= betAmount;
        System.out.println("Bet placed. Balance after bet: " + balance + " FUN");

        // Create UUID for srg with backup workers
        String requestId = UUID.randomUUID().toString();

        PlayGameRequest playGameRequest = new PlayGameRequest(playerId, gameName, betAmount);
        Message<PlayGameRequest> message = new Message<>(Sender.PLAYER, PlayerActions.PLAY_GAME, playGameRequest, requestId);

        Object response = sendMessageToMaster(message);

        if (response instanceof String) {
            System.out.println(response);
            balance += betAmount;
            System.out.println("Response from server: " + response);
            System.out.println("Bet refunded. Balance restored to: " + balance + " FUN");

        } else {
            PlayGameResult playGameResult = (PlayGameResult) response;
            applyResult(playGameResult.getResultType(), playGameResult.getWinAmount(), betAmount);

        }
    }

    public void rateGame(Scanner scanner) {
        System.out.println("\n========== Rate serializables.Game ==========");
        System.out.print("Enter serializables.Game Name: ");
        String gameName = scanner.nextLine().trim();
        if (gameName.isEmpty()) {
            System.out.println("serializables.Game name cannot be empty.");
            return;
        }

        System.out.print("Enter Rating (1-5): ");
        String ratingInput = scanner.nextLine().trim();
        try {
            int rating = Integer.parseInt(ratingInput);
            if (rating < 1 || rating > 5) {
                System.out.println("Invalid rating. Please enter a number between 1 and 5.");
                return;
            }
            RateGameRequest payload = new RateGameRequest(gameName, rating);
            Message<RateGameRequest> message = new Message<>(Sender.PLAYER, PlayerActions.RATE_GAME, payload, null);
            Object response = sendMessageToMaster(message);
            System.out.println("Response from server: " + response);

        } catch (NumberFormatException e) {
            System.out.println("Error: Invalid format. Rating must be a number.");
        }

    }

    public void addBalance(Scanner scanner) {
        System.out.println("\n========== Add Balance ==========");
        System.out.println("Current balance: " + balance + " FUN");

        System.out.print("Enter Amount to Add: ");
        String amountInput = scanner.nextLine().trim();

        try {
            float amount = Float.parseFloat(amountInput);
            if (amount <= 0) {
                System.out.println("Amount must be positive.");
                return;
            }
            balance += amount;
            System.out.println("Balance updated. New balance: " + balance + " FUN");
        } catch (NumberFormatException e) {
            System.out.println("Invalid amount.");
        }
    }

    // -- Helper Methods
    private Integer getMinStars(Scanner scanner) {
        while (true) {
            System.out.println("Enter minimum stars (1-5, or leave blank): ");
            String starsInput = scanner.nextLine().trim();
            if (starsInput.isEmpty()) return null;
            try {
                int stars = Integer.parseInt(starsInput);
                if (stars >= 1 && stars <= 5) return stars;
                System.out.println("Please enter a number between 1 and 5.");
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a valid number.");
            }
        }
    }

    private BetCategory getBetCategory(Scanner scanner) {
        while (true) {
            System.out.println("Enter betting category ($ / $$ / $$$ or leave blank): ");
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) return null;
            switch (input) {
                case "$":   return BetCategory.LOW;
                case "$$":  return BetCategory.MEDIUM;
                case "$$$": return BetCategory.HIGH;
                default:    System.out.println("Invalid category. Please enter $, $$, or $$$.");
            }
        }
    }

    private RiskLevel getRiskLevel(Scanner scanner) {
        while (true) {
            System.out.println("Enter risk level (low / medium / high or leave blank): ");
            String riskInput = scanner.nextLine().trim().toUpperCase();
            if (riskInput.isEmpty()) return null;
            try {
                return RiskLevel.valueOf(riskInput);
            } catch (IllegalArgumentException e) {
                System.out.println("Invalid risk level. Please enter low, medium, or high.");
            }
        }
    }

    private float getBetAmount(Scanner scanner) {
        while (true) {
            System.out.print("Enter Bet Amount: ");
            String betInput = scanner.nextLine().trim();
            try {
                float amount = Float.parseFloat(betInput);
                if (amount > 0) return amount;
                System.out.println("Bet amount must be positive.");
            } catch (NumberFormatException e) {
                System.out.println("Invalid amount. Please enter a number.");
            }
        }
    }

    private void applyResult(PlayGameResultType resultType, float winAmount, float betAmount) {
        switch (resultType) {
            case JACKPOT:
                balance += winAmount;
                System.out.println("JACKPOT! You won " + (winAmount - betAmount) + " FUN!");
                break;
            case WIN:
                balance += winAmount;
                System.out.println("WIN! You won " + (winAmount - betAmount) + " FUN!");
                break;
            case BREAK_EVEN:
                balance += winAmount;
                System.out.println("BREAK EVEN! You got your bet back.");
                break;
            case PARTIAL_LOSS:
                balance += winAmount;
                System.out.println("PARTIAL LOSS.You lost " + Math.abs(winAmount - betAmount) + ". At least you didn't lose everything :)");
                break;
            case LOSS:
                System.out.println("LOSS. Better luck next time.");
                break;
        }
        System.out.println("New balance: " + balance + " FUN");
    }

    private Object sendMessageToMaster(Message<?> message) {
        try {

            FileInputStream fis = new FileInputStream("src/config.properties");
            prop.load(fis);

            String masterIp = prop.getProperty("master.ip");
            int masterPort = Integer.parseInt(prop.getProperty("master.port"));

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
}