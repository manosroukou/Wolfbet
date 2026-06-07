import model.enums.*;
import serializables.*;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.*;

public class HandleThreadWorker extends Thread{
    private final Socket client;
    private final GameRepository localGames;
    private final Map<String, GameRepository> workerToGames;
    private final Properties prop;

    private final float[] lowMultipliers;
    private final float[] mediumMultipliers;
    private final float[] highMultipliers;

    public HandleThreadWorker(
            Socket client,
            GameRepository localGames,
            Map<String, GameRepository> workerToGames,
            Properties prop,
            float[] lowMultipliers,
            float[] mediumMultipliers,
            float[] highMultipliers
    ) {
        this.client = client;
        this.localGames = localGames;
        this.prop = prop;
        this.workerToGames = workerToGames;
        this.lowMultipliers = lowMultipliers;
        this.mediumMultipliers = mediumMultipliers;
        this.highMultipliers = highMultipliers;
    }


    @Override
    public void run() {
        try (
                client;
                ObjectOutputStream out = new ObjectOutputStream(client.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(client.getInputStream())
        ) {
            MessageWithWorkerMode messageWithWorkerMode = (MessageWithWorkerMode) in.readObject();
            handleMessage(messageWithWorkerMode, out);

        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void handleMessage(MessageWithWorkerMode messageWithWorkerMode, ObjectOutputStream out) throws IOException {
        WorkerMode mode = messageWithWorkerMode.workerMode();

        // Resolve which GameRepository to use
        GameRepository games = null;
        if (mode != null) {
            if (mode == WorkerMode.MAIN) {
                System.out.println("[Worker] Handling request as " + mode +" worker");
                games = localGames;
            } else if (mode == WorkerMode.LEADER || mode == WorkerMode.BACKUP) {
                // LEADER or BACKUP
                System.out.println("[Worker] Handling request as " + mode + " worker");
                String mainWorkerKey = "worker" + messageWithWorkerMode.workerId();
                games = workerToGames.computeIfAbsent(mainWorkerKey, k -> new GameRepository());
            }
        }

        Sender sender = messageWithWorkerMode.message().getSender();
        ActionType action = messageWithWorkerMode.message().getAction();

        if (sender == Sender.MANAGER) {

            if (action == ManagerActions.CREATE_GAME) {
                out.writeObject(handleCreateGame(messageWithWorkerMode, games));
                out.flush();

            } else if (action == ManagerActions.DELETE_GAME) {
                out.writeObject(handleDeleteGame(messageWithWorkerMode, games));
                out.flush();

            } else if (action == ManagerActions.MODIFY_GAME) {
                out.writeObject(handleModifyGame(messageWithWorkerMode, games));
                out.flush();

            } else if (action == ManagerActions.PNL_PER_GAME) {
                if (messageWithWorkerMode.workerMode() != WorkerMode.BACKUP) {
                    out.writeObject(handlePnlPerGame(messageWithWorkerMode, games));
                    out.flush();
                }

            } else if (action == ManagerActions.PNL_PER_PLAYER) {
                if (messageWithWorkerMode.workerMode() != WorkerMode.BACKUP) {
                    handlePnlPerPlayer(messageWithWorkerMode, games);
                }

            } else if (action == ManagerActions.PNL_PER_PROVIDER) {
                if (messageWithWorkerMode.workerMode() != WorkerMode.BACKUP) {
                    handlePnlPerProvider(messageWithWorkerMode, games);
                }

            } else if (action == ManagerActions.REESTABLISH_GAME) {
                out.writeObject(handleReestablishGame(messageWithWorkerMode, games));
                out.flush();
            }

        } else if (sender == Sender.PLAYER) {

            if (action == PlayerActions.PLAY_GAME) {
                out.writeObject(handlePlayGame(messageWithWorkerMode, games));
                out.flush();

            } else if (action == PlayerActions.FILTER_GAMES) {
                if (messageWithWorkerMode.workerMode() != WorkerMode.BACKUP) {
                    handleFilterGames(messageWithWorkerMode, games);
                }

            } else if (action == PlayerActions.SHOW_AVAILABLE_GAMES) {
                if (messageWithWorkerMode.workerMode() != WorkerMode.BACKUP) {
                    handleShowAvailableGames(messageWithWorkerMode, games);
                }

            } else if (action == PlayerActions.RATE_GAME) {
                out.writeObject(handleRateGame(messageWithWorkerMode, games));
                out.flush();
            }

        } else if (sender == Sender.MASTER_HEALTH_CHECK_DAEMON) {
            out.writeObject(handleHealthCheck(messageWithWorkerMode));
            out.flush();
        }

    }

    // =================
    // ==== MANAGER ====
    // =================

    private String handleCreateGame(MessageWithWorkerMode messageWithWorkerMode, GameRepository games) {
        CreateGameRequest payload = (CreateGameRequest) messageWithWorkerMode.message().getPayload();

        Game game = payload.getGame();

        // Check if game already exists and is not deleted
        if (games.containsGame(game.getName()) && games.getWorkerGame(game.getName()).isEnabled()) {
            System.out.println("[Worker] Received action to create game: " + game.getName() +" but game already exists");
            return "Game " + game.getName() + " already exists.";
        }

        byte[] gameLogo = payload.getGameLogoPNG();
        float minBet = game.getMinBet();
        float jackpot = calculateJackpot(game.getRiskLevel());
        BetCategory betCategory = calculateBetCategory(minBet);

        // Construct WorkerGame object from Game object and add it to Worker games
        WorkerGame workerGame = new WorkerGame(game, jackpot, betCategory, gameLogo);
        games.addWorkerGame(workerGame);
        System.out.println("[Worker] Received action for create game: Game " + game.getName() + " stored successfully inside worker");

        // Communicate with SRG to start publishers for this new game (only if you are Main)
        if (messageWithWorkerMode.workerMode() != WorkerMode.BACKUP) {
            String srgIp = prop.getProperty("srg.ip");
            int srgPort = Integer.parseInt(prop.getProperty("srg.port"));
            try {
                Socket workerSocket = new Socket(srgIp, srgPort);
                ObjectOutputStream out = new ObjectOutputStream(workerSocket.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(workerSocket.getInputStream());

                out.writeObject(messageWithWorkerMode);
                out.flush();

                SRGResponse response = (SRGResponse) in.readObject();
                System.out.println(response.getTextMessage());
                return "[Worker] Srg for this game has been enabled. Game created successfully";
            } catch (IOException | ClassNotFoundException e) {
                System.out.println("Could not communicate with SRG server");
                return "Error: SRG is not responding.";
            }
        }
        return "Back-up received";
    }

    private String handleDeleteGame(MessageWithWorkerMode messageWithWorkerMode, GameRepository games) {
        String gameName = (String) messageWithWorkerMode.message().getPayload();

        WorkerGame workerGame = games.getWorkerGame(gameName);

        if (workerGame == null) {
            System.out.println("Received action for delete game, but game does not exist");
            return "Error: Game " + gameName + " not found.";
        }

        if (!workerGame.isEnabled()) {
            System.out.println("Received action for delete game, but game has already been deleted");
            return "Game " + gameName + " is already disabled.";
        }

        workerGame.setEnabled(false);
        System.out.println("Received action for delete game, game deleted successfully");

        // Communicate to SRG to stop publishers for this game (if worker main)
        if (messageWithWorkerMode.workerMode() != WorkerMode.BACKUP) {
            Message messageToSRG = new Message(Sender.MANAGER, ManagerActions.DELETE_GAME, gameName, null);
            MessageWithWorkerMode messageToSrgWithWorkerMode = new MessageWithWorkerMode(messageToSRG, messageWithWorkerMode.workerMode(), null);
            String srgIp = prop.getProperty("srg.ip");
            int srgPort = Integer.parseInt(prop.getProperty("srg.port"));
            try {
                Socket workerSocket = new Socket(srgIp, srgPort);
                ObjectOutputStream out = new ObjectOutputStream(workerSocket.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(workerSocket.getInputStream());

                out.writeObject(messageToSrgWithWorkerMode);
                out.flush();

                SRGResponse response = (SRGResponse) in.readObject();
                System.out.println(response.getTextMessage());
                return "Game disabled successfully";
            } catch (IOException | ClassNotFoundException e) {
                System.out.println("Could not communicate with SRG server");
                return "Error: SRG is not responding.";
            }
        }
        return "";
    }

    private String handleModifyGame(MessageWithWorkerMode messageWithWorkerMode, GameRepository games) {
        ModifyGameRequest modifyGameRequest = (ModifyGameRequest) messageWithWorkerMode.message().getPayload();
        String gameName = modifyGameRequest.getGameName();

        if (!games.containsGame(gameName) || !games.getWorkerGame(gameName).isEnabled()) {
            System.out.println("Received action to modify game but game does not exist or it has been disabled.");
            return "Error: Game " + gameName + " does not exist or has been disabled.";
        }

        Game game = games.getWorkerGame(gameName).getGame();

        // Resolve final values (new if provided, existing otherwise)
        float resolvedMin = modifyGameRequest.getMinBet() != null ? modifyGameRequest.getMinBet() : game.getMinBet();
        float resolvedMax = modifyGameRequest.getMaxBet() != null ? modifyGameRequest.getMaxBet() : game.getMaxBet();
        RiskLevel resolvedRisk = modifyGameRequest.getRiskLevel() != null ? modifyGameRequest.getRiskLevel() : game.getRiskLevel();

        // Cross-field validation using resolved values
        if (resolvedMin >= resolvedMax) {
            return "Error: Min Bet must be less than Max Bet.";
        }

        if (modifyGameRequest.getMinBet() != null)
            game.modifyMinBet(resolvedMin);

        if (modifyGameRequest.getMaxBet() != null)
            game.modifyMaxBet(resolvedMax);

        if (modifyGameRequest.getRiskLevel() != null)
            game.modifyRiskLevel(resolvedRisk);

        // Calculate new jackpot and BetCategory
        float newJackpot = calculateJackpot(resolvedRisk);
        BetCategory newBetCategory = calculateBetCategory(resolvedMin);

        // Save changes
        games.getWorkerGame(gameName).modifyJackpot(newJackpot);
        games.getWorkerGame(gameName).modifyBetCategory(newBetCategory);


        System.out.println("Received action to modify game: Game " + gameName + " modified successfully.");
        return "Game " + gameName + " modified successfully.";
    }

    private String handleReestablishGame(MessageWithWorkerMode messageWithWorkerMode, GameRepository games) {
        String gameName = (String) messageWithWorkerMode.message().getPayload();

        if (games.containsGame(gameName) && !games.getWorkerGame(gameName).isEnabled()) {
            games.getWorkerGame(gameName).setEnabled(true);

            if (messageWithWorkerMode.workerMode() != WorkerMode.BACKUP) {
                try {
                    String srgIp = prop.getProperty("srg.ip");
                    int srgPort = Integer.parseInt(prop.getProperty("srg.port"));
                    Socket srgSocket = new Socket(srgIp, srgPort);

                    ObjectOutputStream workerOut = new ObjectOutputStream(srgSocket.getOutputStream());
                    ObjectInputStream workerIn = new ObjectInputStream(srgSocket.getInputStream());

                    Message messageToSrg = new Message(Sender.MANAGER, ManagerActions.REESTABLISH_GAME, gameName, null);
                    MessageWithWorkerMode messageToSrgWithWorkerMode = new MessageWithWorkerMode(messageToSrg, messageWithWorkerMode.workerMode(), null);

                    workerOut.writeObject(messageToSrgWithWorkerMode);
                    workerOut.flush();

                    SRGResponse response = (SRGResponse) workerIn.readObject();
                    System.out.println(response.getTextMessage());

                    srgSocket.close();
                } catch (IOException | ClassNotFoundException e) {
                    System.err.println("Warning: Could not send result to Reducer: " + e.getMessage());
                }
            }

            System.out.println("Received action to reestablish game and succeeded.");
            return "success for disabling game";
        } else {
            System.out.println("Received action to reestablish game but game never existed");
            return "Error: Game " + gameName + " never existed in the first place";
        }

    }

    private Object handlePnlPerGame(MessageWithWorkerMode messageWithWorkerMode, GameRepository games) {

        String gameName = (String) messageWithWorkerMode.message().getPayload();
        return games.getWorkerGame(gameName).getTotalProfits();

    }

    private void handlePnlPerPlayer(MessageWithWorkerMode messageWithWorkerMode, GameRepository games) {
        String id = messageWithWorkerMode.message().getRequestId();
        String playerId = (String) messageWithWorkerMode.message().getPayload();

        // Collect all available games that player has played
        Collection<WorkerGame> results = new ArrayList<>();

        for (WorkerGame workerGame: games.getAll()) {
            if (workerGame.hasPlayer(playerId)) {
                results.add(workerGame);
            }
        }

        ReduceRequest messageToSrg = new ReduceRequest(results, id);
        sendToReducer(messageToSrg);


    }

    private void handlePnlPerProvider(MessageWithWorkerMode messageWithWorkerMode, GameRepository games) {
        String id = messageWithWorkerMode.message().getRequestId();
        String providerName = (String) messageWithWorkerMode.message().getPayload();

        // Collect all available games from this provider
        Collection<WorkerGame> results = new ArrayList<>();

        for (WorkerGame workerGame: games.getAll()) {
            if (workerGame.getGame().getProvider().equals(providerName)) {
                results.add(workerGame);
            }
        }

        ReduceRequest messageToSrg = new ReduceRequest(results, id);
        sendToReducer(messageToSrg);


    }

    // =================
    // ===== PLAYER ====
    // =================

    private Object handlePlayGame(MessageWithWorkerMode messageWithWorkerMode, GameRepository games) {
        String requestId =  messageWithWorkerMode.message().getRequestId();
        PlayGameRequest payload = (PlayGameRequest) messageWithWorkerMode.message().getPayload();

        String playerId = payload.getPlayerId();
        String gameName = payload.getGameName();
        float betAmount = payload.getBetAmount();

        // Find game
        WorkerGame workerGame = games.getWorkerGame(gameName);
        if (workerGame == null) {
            return "Error: Game " + gameName + " not found.";
        }

        if (!workerGame.isEnabled()) {
            return "Error: Game " + gameName + " is disabled.";
        }

        // Load random number and hash from srg
        Integer randomNumber = null;
        String hashFromSrg = null;

        try {
            String srgIp = prop.getProperty("srg.ip");
            int srgPort = Integer.parseInt(prop.getProperty("srg.port"));
            Socket srgSocket = new Socket(srgIp, srgPort);

            ObjectOutputStream workerOut = new ObjectOutputStream(srgSocket.getOutputStream());
            ObjectInputStream workerIn = new ObjectInputStream(srgSocket.getInputStream());

            Message message = new Message(Sender.PLAYER, PlayerActions.PLAY_GAME, gameName, requestId);
            MessageWithWorkerMode messageWithWorkerModeToSrg = new MessageWithWorkerMode(message, messageWithWorkerMode.workerMode(), null);

            workerOut.writeObject(messageWithWorkerModeToSrg);
            workerOut.flush();

            SRGResponse response = (SRGResponse) workerIn.readObject();

            randomNumber = response.getRandomNumber();
            hashFromSrg = response.getSha256();

            srgSocket.close();
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("[Error]: Could not send result to Reducer: " + e.getMessage());
        }


        if (randomNumber == null || hashFromSrg == null) {
            return "[Worker]: Buffer for this game does not exist";
        }

        String expectedHash = HashUtil.sha256(randomNumber, games.getWorkerGame(gameName).getGame().getHashKey());
        if (!hashFromSrg.equals(expectedHash)) {
            return "[Worker]: Hash key has been corrupted";
        }
        // Calculate result
        float multiplier;
        PlayGameResultType resultType;

        int mod100 = randomNumber % 100;
        if (mod100 == 0) {
            // JACKPOT
            multiplier = (float) workerGame.getJackpot();
            resultType = PlayGameResultType.JACKPOT;

        } else {
            // Regular Result: index = randomNumber % 10
            int index = randomNumber % 10;
            multiplier = getMultiplier(workerGame.getGame().getRiskLevel(), index);

            if (multiplier == 0.0f) {
                resultType = PlayGameResultType.LOSS;
            } else if (multiplier < 1.0f) {
                resultType = PlayGameResultType.PARTIAL_LOSS;
            } else if (multiplier == 1.0f) {
                resultType = PlayGameResultType.BREAK_EVEN;
            } else {
                resultType = PlayGameResultType.WIN;
            }
        }
        float winAmount = betAmount * multiplier;

        // Update statistics for serializables.WorkerGame
        workerGame.recordBet(playerId, betAmount, multiplier);
        return new PlayGameResult(resultType, winAmount);

    }

    private void handleShowAvailableGames(MessageWithWorkerMode messageWithWorkerMode, GameRepository games) {
        String id = messageWithWorkerMode.message().getRequestId();

        // Collect all available games
        Collection<WorkerGame> results = new ArrayList<>();

        for (WorkerGame workerGame : games.getAll()) {
            if (workerGame.isEnabled()) {
                results.add(workerGame);
            }
        }

        ReduceRequest messageToReducer = new ReduceRequest(results, id);
        sendToReducer(messageToReducer);
    }

    private void handleFilterGames (MessageWithWorkerMode messageWithWorkerMode, GameRepository games) {
        String id = messageWithWorkerMode.message().getRequestId();
        SearchFilters filters = (SearchFilters) messageWithWorkerMode.message().getPayload();
        Collection<WorkerGame> results = new ArrayList<>();

        // Search for WorkerGames
        for (WorkerGame workerGame : games.getAll()) {
            if (!workerGame.isEnabled()) {
                continue;
            }
            if (matchesFilters(workerGame, filters)) {
                results.add(workerGame);
            }
        }

        ReduceRequest messageToReducer = new ReduceRequest(results, id);
        sendToReducer(messageToReducer);
    }

    private Object handleRateGame(MessageWithWorkerMode messageWithWorkerMode, GameRepository games) {

        RateGameRequest payload = (RateGameRequest) messageWithWorkerMode.message().getPayload();

        String gameName = payload.getGameName();
        int rating = payload.getRating();

        WorkerGame workerGame = games.getWorkerGame(gameName);

        if (workerGame == null || !workerGame.isEnabled()) {
            return "Game not found.";
        }

        workerGame.addRating(rating);

        return "Rating added successfully!";
    }

    // ================================
    // MASTER HEALTH DAEMON FOR BACKUPS
    // ================================

    private Object handleHealthCheck(MessageWithWorkerMode messageWithWorkerMode) {
        HealthCheckRequest request = (HealthCheckRequest) messageWithWorkerMode.message().getPayload();
        int workerId = request.workerId();
        HealthCheckType type = request.type();
        HealthCheckAction action = request.action();

        return switch (action) {
            case REQUEST -> switch (type) {
                case SELF -> {
                    System.out.println("[HealthCheck] Returning local state, games: " + localGames.getAll().size());
                    yield localGames;
                }
                case FOREIGN -> {
                    GameRepository repo = workerToGames.getOrDefault("worker" + workerId, new GameRepository());
                    System.out.println("[HealthCheck] Returning foreign state for worker" + workerId + ", games: " + repo.getAll().size());
                    yield repo;
                }
            };
            case UPDATE -> {
                switch (type) {
                    case SELF -> {
                        localGames.replaceAll(request.state());
                        System.out.println("[HealthCheck] Local state updated, games: " + localGames.getAll().size());
                    }
                    case FOREIGN -> {
                        workerToGames.put("worker" + workerId, request.state());
                        System.out.println("[HealthCheck] Foreign state updated for worker" + workerId + ", games: " + request.state().getAll().size());
                    }
                }
                yield "State updated successfully";
            }
        };
    }

    // -- Helper Methods

    private float calculateJackpot(RiskLevel riskLevel) {
        switch (riskLevel) {
            case LOW:
                return 10.0f;
            case MEDIUM:
                return 20.0f;
            case HIGH:
                return 40.0f;
            default:
                throw new IllegalArgumentException("Unknown risk level: " + riskLevel);
        }
    }

    private BetCategory calculateBetCategory(float minBet) {
        if (minBet >= 5f) {
            return BetCategory.HIGH;
        } else if (minBet >= 1f) {
            return BetCategory.MEDIUM;
        } else if (minBet >= 0.1f) {
            return BetCategory.LOW;
        } else {
            System.out.println("Error in calculateBetCategory: minBet below minimum allowed value.");
            return null;
        }
    }

    private float getMultiplier(RiskLevel riskLevel, int index) {
        return switch (riskLevel) {
            case LOW -> lowMultipliers[index];
            case MEDIUM -> mediumMultipliers[index];
            case HIGH -> highMultipliers[index];
        };
    }

    private boolean matchesFilters(WorkerGame workerGame, SearchFilters filters) {
        Game game = workerGame.getGame();

        // Έλεγχος αστεριών
        if (filters.getMinStars() != null) {
            if (game.getStars() < filters.getMinStars()) return false;
        }

        // Έλεγχος κατηγορίας πονταρίσματος
        if (filters.getBetCategory() != null) {
            if (workerGame.getBetCategory() != filters.getBetCategory()) return false;
        }

        // Έλεγχος επιπέδου ρίσκου
        if (filters.getRiskLevel() != null) {
            if (game.getRiskLevel() != filters.getRiskLevel()) return false;
        }

        return true;
    }

    private void sendToReducer(ReduceRequest messageToReducer) {
        try {
            String reducerIp = prop.getProperty("reducer.ip");
            int reducerPort = Integer.parseInt(prop.getProperty("reducer.port"));
            Socket reducerSocket = new Socket(reducerIp, reducerPort);

            ObjectOutputStream reducerOut = new ObjectOutputStream(reducerSocket.getOutputStream());
            ObjectInputStream reducerIn = new ObjectInputStream(reducerSocket.getInputStream());

            reducerOut.writeObject(messageToReducer);
            reducerOut.flush();
            //reducerIn.readObject();

            // Reducer never communicates back to worker so close immediately
            reducerSocket.close();

        } catch (IOException e) {
            System.err.println("Warning: Could not send result to Reducer: " + e.getMessage());
        }
    }
}



