import model.enums.*;
import serializables.Message;
import serializables.PlayGameRequest;
import serializables.RateGameRequest;

import java.io.*;
import java.net.Socket;
import java.util.*;

public class HandleThreadMaster extends Thread {

    private final Socket client;
    private final PendingRequestRepository pendingRequestRepository;
    private final Properties prop;
    private final GameToWorkersMap gameToWorkers; // The first element of each list corresponds to the main worker, the rest are backup workers
    private final DownWorkersLog downWorkersLog;  // must be concurrent

    public HandleThreadMaster(Socket client, PendingRequestRepository pendingRequestRepository,
                              Properties prop, GameToWorkersMap gameToWorkers,
                              DownWorkersLog downWorkersLog) {
        this.client = client;
        this.pendingRequestRepository = pendingRequestRepository;
        this.prop = prop;
        this.gameToWorkers = gameToWorkers;
        this.downWorkersLog = downWorkersLog;
    }

    @Override
    public void run() {
        try (
                ObjectOutputStream out = new ObjectOutputStream(client.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(client.getInputStream())
        ) {
            Message<?> message = (Message<?>) in.readObject();

            if (message.getSender() != Sender.REDUCER) {
                Object response = handleMessage(message);
                out.writeObject(response);
                out.flush();
            } else {
                handleMessage(message);
            }

        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private Object handleMessage(Message<?> message) throws IOException {
        Sender sender = message.getSender();
        ActionType action = message.getAction();

        if (sender == Sender.MANAGER) {
            if (action == ManagerActions.CREATE_GAME) {
                return handleCreateGame(message);

            } else if (action == ManagerActions.DELETE_GAME) {
                return handleDeleteGame(message);

            } else if (action == ManagerActions.MODIFY_GAME) {
                return handleModifyGame(message);

            } else if (action == ManagerActions.PNL_PER_GAME) {
                String gName = (String) message.getPayload();
                return forwardToMainOnly(message, gName);

            } else if (action == ManagerActions.PNL_PER_PLAYER) {
                return handleMapReduceAction(message);

            } else if (action == ManagerActions.PNL_PER_PROVIDER) {
                return handleMapReduceAction(message);

            } else if (action == ManagerActions.REESTABLISH_GAME) {
                String gName = (String) message.getPayload();
                return forwardToMainOnly(message, gName);
            }

        } else if (sender == Sender.PLAYER) {

            if (action == PlayerActions.PLAY_GAME) {
                return handlePlayGame(message);

            } else if (action == PlayerActions.FILTER_GAMES) {
                return handleMapReduceAction(message);

            } else if (action == PlayerActions.SHOW_AVAILABLE_GAMES) {
                return handleMapReduceAction(message);

            } else if (action == PlayerActions.RATE_GAME) {
                return handleRateGame(message);
            }
        } else if (sender == Sender.REDUCER) {
            // Extract requestId to finish a pending task
            String requestId = message.getRequestId();

            PendingRequest pending = pendingRequestRepository.get(requestId);
            if (pending != null) {
                pending.complete(message.getPayload());
                return "Message Returned Successfully";
            }
        }

        // Error occurred
        return "ERROR: Unsupported combination - Sender: " + message.getSender() + ", Action: " + message.getAction();
    }

    // =================
    // ==== MANAGER ====
    // =================

    private Object handleCreateGame(Message<?> message) {
        CreateGameRequest payload = (CreateGameRequest) message.getPayload();
        String gameName = payload.getGame().getName();

        int noOfWorkers = Integer.parseInt(prop.getProperty("workers"));
        int mainWorker = chooseWorker(gameName, noOfWorkers);
        List<Integer> backupWorkers = chooseBackupWorkers(mainWorker, noOfWorkers);

        // Create a list, with the first item being the main worker, and the rest being backup workers
        List<Integer> allWorkers = new ArrayList<>();
        allWorkers.add(mainWorker);
        allWorkers.addAll(backupWorkers);

        gameToWorkers.put(gameName, allWorkers);
        System.out.println("[DEBUG]" + " Game received. Workers for this game: " + gameToWorkers.get(gameName));

        System.out.println("Game received");
        return forwardMessage(message, gameName);
    }

    private Object handleDeleteGame(Message<?> message) {
        // Payload here contains String(the name of the game we want to delete)
        String gameName = (String) message.getPayload();
        return forwardMessage(message, gameName);
    }

    private Object handleModifyGame(Message<?> message) {
        ModifyGameRequest modifyRequest = (ModifyGameRequest) message.getPayload();

        String gameName = modifyRequest.getGameName();
        return forwardMessage(message, gameName);
    }

    private Object handleMapReduceAction(Message<?> message) throws RuntimeException {

        // We need to store the request id and wait for the reducer
        String requestId = message.getRequestId();
        // Check for duplicates (will probably never trigger for uuids)
        if (pendingRequestRepository.get(requestId) != null) {
            return "ERROR: Duplicate request id.";
        }

        PendingRequest pendingRequest = new PendingRequest(requestId);
        pendingRequestRepository.add(pendingRequest);

        // Make the current thread wait until reducer comes back with a result
        // How it works: Wait for the same id to arrive and mark the pending request as complete.
        try {
            forwardToAllWorkers(message);
            return pendingRequest.awaitResult(); // Blocks. Will return when reducer arrives with result
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            pendingRequestRepository.remove(requestId);
        }

    }

    // =================
    // ===== PLAYER ====
    // =================

    private Object handlePlayGame(Message<?> message) {
        PlayGameRequest payload = (PlayGameRequest) message.getPayload();
        String gameName = payload.getGameName();
        return forwardMessage(message, gameName);

    }

    private Object handleRateGame(Message<?> message) {
        RateGameRequest payload = (RateGameRequest) message.getPayload();

        String gameName = payload.getGameName();
        return forwardMessage(message, gameName);
    }

    // -- Helper Methods

    private Object forwardMessage(Message<?> message, String gameName) {
        // Extract list with workers (main and back-ups) for the game provided.
        List<Integer> allWorkers = gameToWorkers.get(gameName);
        System.out.println("[DEBUG]" + allWorkers);

        Object result = null;
        int lastI = 0;
        int timeout = Integer.parseInt(prop.getProperty("healthcheck.timeout"));

        // Find the first alive worker and forward as MAIN or LEADER
        for (int i = 1; i <= allWorkers.size(); i++) {
            String workerIp = prop.getProperty("worker" + allWorkers.get(i - 1) + ".ip");
            int workerPort = Integer.parseInt(prop.getProperty("worker" + allWorkers.get(i - 1) + ".port"));
            int healthPort = Integer.parseInt(prop.getProperty("worker" + allWorkers.get(i - 1) + ".healthport"));

            boolean alive = Master.checkAlive(workerIp, healthPort, timeout);
            System.out.println("[HealthCheck]" + " Worker " + allWorkers.get(i - 1) + " is alive: " +  alive);

            if (!alive) {
                downWorkersLog.markDown("worker" + allWorkers.get(i - 1));

                // This scenario is interesting. The worker turned alive just after the stateCanBeResolved.
                // Since the table entry hasn't been updated we move on with it as being false and a next thread will handle resolving it.
            } else if (!downWorkersLog.isUp("worker" + allWorkers.get(i - 1))) {
                // Do nothing, another thread can resolve it later

            } else {
                WorkerMode mode = (i == 1) ? WorkerMode.MAIN : WorkerMode.LEADER;
                MessageWithWorkerMode messageToWorker = (i == 1) ? new MessageWithWorkerMode(message, mode, null)
                        : new MessageWithWorkerMode(message, mode, allWorkers.getFirst());
                result = forwardToWorker(messageToWorker, workerIp, workerPort);
                lastI = i;
                break;
            }

        }

        // No worker is alive
        if (lastI == 0) {
            return "[Error] No available worker for game " + gameName;
        }

        // Forward to back up workers only for write actions
        // Ignore result
        forwardToBackupWorkers(message, allWorkers, lastI, timeout);

        return result;
    }

    private Object forwardToMainOnly(Message<?> message, String gameName) {
        List<Integer> allWorkers = gameToWorkers.get(gameName);
        int timeout = Integer.parseInt(prop.getProperty("healthcheck.timeout"));

        for (int i = 0; i < allWorkers.size(); i++) {
            int workerId = allWorkers.get(i);
            String workerIp = prop.getProperty("worker" + workerId + ".ip");
            int workerPort = Integer.parseInt(prop.getProperty("worker" + workerId + ".port"));
            int healthPort = Integer.parseInt(prop.getProperty("worker" + workerId + ".healthport"));

            boolean alive = Master.checkAlive(workerIp, healthPort, timeout);

            if (!alive) {
                downWorkersLog.markDown("worker" + workerId);
            } else if (!downWorkersLog.isUp("worker" + workerId)) {
                // Alive but unresolved — skip
            } else {
                WorkerMode mode = (i == 0) ? WorkerMode.MAIN : WorkerMode.LEADER;
                MessageWithWorkerMode msg = new MessageWithWorkerMode(message, mode, (i == 0) ? null : allWorkers.getFirst());
                return forwardToWorker(msg, workerIp, workerPort);
            }
        }

        return "[Error] No available worker for game " + gameName;
    }

    private void forwardToBackupWorkers(Message<?> message, List<Integer> allWorkers, int startIndex, int timeout) {
        for (int i = startIndex; i < allWorkers.size(); i++) {
            String workerIp = prop.getProperty("worker" + allWorkers.get(i) + ".ip");
            int workerPort = Integer.parseInt(prop.getProperty("worker" + allWorkers.get(i) + ".port"));
            int healthPort = Integer.parseInt(prop.getProperty("worker" + allWorkers.get(i) + ".healthport"));

            boolean alive = Master.checkAlive(workerIp, healthPort, timeout);
            System.out.println("[HealthCheck] Worker " + allWorkers.get(i) + " is alive: " + alive);

            if (!alive) {
                downWorkersLog.markDown("worker" + allWorkers.get(i));
            } else {
                MessageWithWorkerMode messageToWorker = new MessageWithWorkerMode(message, WorkerMode.BACKUP, allWorkers.getFirst());
                forwardToWorker(messageToWorker, workerIp, workerPort);
            }
        }
    }

    private Object forwardToWorker(MessageWithWorkerMode message, String workerIp, int workerPort) {
        try (
                Socket workerSocket = new Socket(workerIp, workerPort);
                ObjectOutputStream workerOut = new ObjectOutputStream(workerSocket.getOutputStream());
                ObjectInputStream workerIn = new ObjectInputStream(workerSocket.getInputStream())
        ) {
            workerOut.writeObject(message);
            workerOut.flush();
            System.out.println("[Debug] - forward complete");

            return workerIn.readObject();
        } catch (IOException | ClassNotFoundException e) {
            return "Error: Worker " + workerIp + " is not responding.";
        }
    }

    private void forwardToAllWorkers(Message<?> message) {
        int noOfWorkers = Integer.parseInt(prop.getProperty("workers"));
        int timeout = Integer.parseInt(prop.getProperty("healthcheck.timeout"));
        List<Thread> threads = new ArrayList<>();

        for (int i = 1; i <= noOfWorkers; i++) {
            final String workerIp = prop.getProperty("worker" + i + ".ip");
            final int workerPort = Integer.parseInt(prop.getProperty("worker" + i + ".port"));
            int healthPort = Integer.parseInt(prop.getProperty("worker" + i + ".healthport"));

            final int workerId = i;

            Thread thread = new Thread(() -> {
                boolean alive = Master.checkAlive(workerIp, healthPort, timeout);

                if (alive && downWorkersLog.isUp("worker" + workerId)) {
                    MessageWithWorkerMode msg = new MessageWithWorkerMode(message, WorkerMode.MAIN, null);
                    forwardToWorker(msg, workerIp, workerPort);
                } else {
                    downWorkersLog.markDown("worker" + workerId);

                    List<Integer> backups = chooseBackupWorkers(workerId, noOfWorkers);
                    for (int backupId : backups) {
                        String backupIp = prop.getProperty("worker" + backupId + ".ip");
                        int backupPort = Integer.parseInt(prop.getProperty("worker" + backupId + ".port"));
                        int backupHealthPort = Integer.parseInt(prop.getProperty("worker" + backupId + ".healthport"));


                        if (Master.checkAlive(backupIp, backupHealthPort, timeout)
                                && downWorkersLog.isUp("worker" + backupId)) {
                            MessageWithWorkerMode msg = new MessageWithWorkerMode(message, WorkerMode.LEADER, workerId);
                            forwardToWorker(msg, backupIp, backupPort);
                            break;
                        }
                    }
                }
            });

            threads.add(thread);
            thread.start();
        }

        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Thread interrupted while waiting for worker forwards");
            }
        }
    }

    private int chooseWorker(String gameName, int noOfWorkers) {
        return Math.floorMod(gameName.hashCode(), noOfWorkers) + 1;
    }

    private List<Integer> chooseBackupWorkers(int mainWorker, int noOfWorkers) {
        int noOfBackups = Integer.parseInt(prop.getProperty("noOfBackupWorkersPerGame"));

        List<Integer> backups = new ArrayList<>();
        for (int i = 1; i <= noOfBackups; i++) {
            int backup = Math.floorMod((mainWorker - 1) + i, noOfWorkers) + 1;  // wrap around circularly
            backups.add(backup);
        }
        return backups;
    }

}
