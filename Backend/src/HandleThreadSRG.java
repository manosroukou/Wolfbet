import model.enums.*;
import serializables.Game;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class HandleThreadSRG extends Thread {
    private final Socket client;
    private final RandomBufferRepository randomBufferRepository;
    private final GameSecretRepository secrets;
    private final PersistentRandomNumberRepository persistentRandomNumberRepository;

    public HandleThreadSRG(Socket client, RandomBufferRepository randomBufferRepository, GameSecretRepository secrets, PersistentRandomNumberRepository randomNumberRepository) {
        this.client = client;
        this.randomBufferRepository = randomBufferRepository;
        this.secrets = secrets;
        this.persistentRandomNumberRepository = randomNumberRepository;

    }

    @Override
    public void run() {

        try {
            // Stream Initialization
            ObjectOutputStream out = new ObjectOutputStream(client.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(client.getInputStream());

            MessageWithWorkerMode message = (MessageWithWorkerMode) in.readObject();
            SRGResponse response = handleMessage(message);

            // Send response back to worker
            out.writeObject(response);
            out.flush();

        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        } finally {
            try {
                client.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    public SRGResponse handleMessage(MessageWithWorkerMode messageWithWorkerMode) {
        Sender sender = messageWithWorkerMode.message().getSender();
        ActionType action = messageWithWorkerMode.message().getAction();

        if (sender == Sender.MANAGER && action == ManagerActions.CREATE_GAME) {
            return handleCreateGame(messageWithWorkerMode);

        } else if (sender == Sender.MANAGER && action == ManagerActions.DELETE_GAME) {
            return handleDeleteGame(messageWithWorkerMode);

        } else if (sender == Sender.PLAYER && action == PlayerActions.PLAY_GAME) {
            return handlePlayGame(messageWithWorkerMode);

        } else if (sender == Sender.MANAGER && action == ManagerActions.REESTABLISH_GAME) {
            return handleReestablishGame(messageWithWorkerMode);
        }
        return new SRGResponse("[Error] Unsupported action or message");
    }

    private SRGResponse handleCreateGame(MessageWithWorkerMode messageWithWorkerMode) {
        CreateGameRequest payload = (CreateGameRequest) messageWithWorkerMode.message().getPayload();
        Game game = payload.getGame();
        String gameName = game.getName();

        if (randomBufferRepository.containsBuffer(gameName)) {
            System.out.println("[SRG] Received action for initiating publishers for a game that already exists");
            return new SRGResponse("[SRG] A game with this name already exists in srg.");
        }

        // Create buffer and place it in the repository
        RandomNumberBuffer buffer = new RandomNumberBuffer(10);
        randomBufferRepository.addBuffer(gameName, buffer);

        // Create entry in the secrets hashMap
        secrets.addSecret(gameName, game.getHashKey());

        RandomNumberProducer producer = new RandomNumberProducer(buffer);
        producer.start();
        System.out.println("[SRG] Received action for initiating publishers for game and publishers activated successfully");
        return new SRGResponse("[SRG] A random number generator has been initialized for game: " + gameName);
    }

    private SRGResponse handleDeleteGame(MessageWithWorkerMode messageWithWorkerMode) {
        String gameName = (String) messageWithWorkerMode.message().getPayload();
        // Retrieve buffer for the specified game
        RandomNumberBuffer buffer = randomBufferRepository.getBuffer(gameName);
        buffer.disableBuffer();
        System.out.println("Received action to delete game and publishers are waiting until game reestablishes");
        return new SRGResponse("Publishers for this game have been disabled.");
    }

    private SRGResponse handlePlayGame(MessageWithWorkerMode messageWithWorkerMode) {
        String gameName = (String) messageWithWorkerMode.message().getPayload();
        String uuid = messageWithWorkerMode.message().getRequestId();
        WorkerMode mode = messageWithWorkerMode.workerMode();

        // Retrieve buffer for the specified game
        RandomNumberBuffer buffer = randomBufferRepository.getBuffer(gameName);
        if (buffer == null) {
            return new SRGResponse("[SRG] This game does not exist");
        }

        int randomNumber;
        try {
            if (mode == WorkerMode.MAIN || mode == WorkerMode.LEADER) {
                randomNumber = buffer.take();   // grab the first randomNumber from the queue
                PersistentRandomNumberWithBackUpCount persistent = new PersistentRandomNumberWithBackUpCount(randomNumber);     //store the random number, noOfBackUp argument is set to zero
                persistentRandomNumberRepository.addRandomNumberWithBackUpCount(uuid, persistent); // store it so that back workers replay this action with this number

            } else {

                // If you are backup, grab random number from repo
                randomNumber = persistentRandomNumberRepository.getRandomNumberWithBackUpCount(uuid).getPersistentRandomNumber();
                persistentRandomNumberRepository.getRandomNumberWithBackUpCount(uuid).updateNoOfBackUpsVisited();

                // Delete it after all backup workers have received it
                if (persistentRandomNumberRepository.getRandomNumberWithBackUpCount(uuid).getNoOfBackUpsVisited() == 2) {
                    persistentRandomNumberRepository.removePersistentRandomNumber(uuid);
                }
            }

            String sha256Hash = HashUtil.sha256(randomNumber, secrets.getSecret(gameName));
            return new SRGResponse(randomNumber, sha256Hash);

        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

    }

    private SRGResponse handleReestablishGame(MessageWithWorkerMode messageWithWorkerMode) {
        String gameName = (String) messageWithWorkerMode.message().getPayload();
        // Retrieve buffer for the specified game
        RandomNumberBuffer buffer = randomBufferRepository.getBuffer(gameName);
        buffer.enableBuffer();
        System.out.println("Received action to reestablish game and publishers are active again");
        return new SRGResponse("Publishers for this game have been re-enabled.");
    }
}
