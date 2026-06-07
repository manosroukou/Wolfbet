import model.enums.HealthCheckAction;
import model.enums.HealthCheckType;
import model.enums.Sender;
import serializables.Message;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.*;
import java.util.*;

public class Master {

    private final PendingRequestRepository pendingRequestRepository = new PendingRequestRepository();
    private final Properties prop = new Properties();
    private final GameToWorkersMap gameToWorkers = new GameToWorkersMap();
    private final DownWorkersLog downWorkersLog = new DownWorkersLog();

    public static void main(String[] args) {
        new Master().start();
    }

    public void start() {
        loadConfigFile();

        initDownWorkersLog();

        startHealthChecker();

        int masterPort = Integer.parseInt(prop.getProperty("master.port"));
        try (ServerSocket serverSocket = new ServerSocket(masterPort)) {
            System.out.println("Master started at port " + masterPort);

            while (true) {
                Socket client = serverSocket.accept();
                new HandleThreadMaster(client, pendingRequestRepository, prop, gameToWorkers, downWorkersLog).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadConfigFile() {
        try {
            FileInputStream fis = new FileInputStream("src/config.properties");
            prop.load(fis);
        } catch (IOException e) {
            throw new RuntimeException("Could not load config.properties", e);
        }
    }

    private void initDownWorkersLog() {
        // At initialization, every worker is considered to be up to the latest state, so we mark them with true
        int noOfWorkers = Integer.parseInt(prop.getProperty("workers"));
        downWorkersLog.init(noOfWorkers);
    }

    private void startHealthChecker() {
        // A thread that runs as a daemon and checks for workers that were down in order to resolve their state
        Thread healthChecker = new Thread(() -> {
            int interval = Integer.parseInt(prop.getProperty("healthcheck.interval"));

            while (true) {
                try {
                    Thread.sleep(interval);
                    System.out.println("[HealthCheck] Start performing health-check for workers");

                    int numOfAliveWorkers = 0;
                    for (Map.Entry<String, Boolean> entry : downWorkersLog.snapshot().entrySet()) {
                        // If entry.getValue() returns false, that means the worker was down at some point,
                        // so we check if its now back in order to resolve its state

                        if (!entry.getValue()) {
                            System.out.println("[HealthCheck] Detected worker with prior or ongoing failure");
                            String workerKey = entry.getKey();
                            String workerIp = prop.getProperty(workerKey + ".ip");
                            int healthPort = Integer.parseInt(prop.getProperty(workerKey + ".healthport"));
                            int timeout = Integer.parseInt(prop.getProperty("healthcheck.timeout"));

                            // If alive, then proceed to resolve state
                            if (checkAlive(workerIp, healthPort, timeout)) {
                                System.out.println("[HealthCheck] " + workerKey + " is back up, resolving state...");
                                resolveState(workerKey);
                                downWorkersLog.markUp(workerKey);
                                System.out.println("[HealthCheck] State resolution finished successfully for " + workerKey);
                            } else {
                                System.out.println("[HealthCheck] Failed worker is still unreachable");
                            }
                        } else {
                            numOfAliveWorkers += 1;
                        }
                    }
                    if (numOfAliveWorkers == downWorkersLog.size()) {
                        System.out.println("[HealthCheck] HealthCheck finished without detecting any failed workers");
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        healthChecker.setDaemon(true);
        healthChecker.start();
    }

    private void resolveState(String workerKey) {
        int workerId = Integer.parseInt(workerKey.replace("worker", ""));
        int noOfWorkers = Integer.parseInt(prop.getProperty("workers"));
        int timeout = Integer.parseInt(prop.getProperty("healthcheck.timeout"));

        String workerIp = prop.getProperty(workerKey + ".ip");
        int workerPort = Integer.parseInt(prop.getProperty(workerKey + ".port"));

        // 1. Recover local state — find a backup that holds this worker's data
        List<Integer> backups = chooseBackupWorkers(workerId, noOfWorkers);
        for (int backupId : backups) {
            String backupIp = prop.getProperty("worker" + backupId + ".ip");
            int backupHealthPort = Integer.parseInt(prop.getProperty("worker" + backupId + ".healthport"));
            int backupPort = Integer.parseInt(prop.getProperty("worker" + backupId + ".port"));

            if (checkAlive(backupIp, backupHealthPort, timeout) && downWorkersLog.isUp("worker" + backupId)) {
                System.out.println("[HealthCheck] Worker" + backupId + " is now resolving local state of Worker" + workerId  );
                // Ask the backup for its copy of this worker's data
                GameRepository localState = requestStateFromWorker(backupIp, backupPort, workerId, HealthCheckType.FOREIGN);
                if (localState != null) {
                    // Send it to the recovered worker so it can restore its local state
                    sendStateToWorker(workerIp, workerPort, workerId, HealthCheckType.SELF, localState);
                    break;
                }
            }
        }

        // 2. Recover foreign state — find which workers this worker backs up
        for (int i = 1; i <= noOfWorkers; i++) {
            if (i == workerId) continue;

            List<Integer> theirBackups = chooseBackupWorkers(i, noOfWorkers);
            if (theirBackups.contains(workerId)) {
                // This worker is a backup for worker i — get worker i's current local state
                String mainIp = prop.getProperty("worker" + i + ".ip");
                int healthPort = Integer.parseInt(prop.getProperty("worker" + i + ".healthport"));
                int mainPort = Integer.parseInt(prop.getProperty("worker" + i + ".port"));

                if (checkAlive(mainIp, healthPort, timeout) && downWorkersLog.isUp("worker" + i)) {
                    GameRepository foreignState = requestStateFromWorker(mainIp, mainPort, i, HealthCheckType.SELF);
                    if (foreignState != null) {
                        // Send it to the recovered worker as its backup copy
                        sendStateToWorker(workerIp, workerPort, i, HealthCheckType.FOREIGN, foreignState);
                    }
                }
            }
        }
    }

    private GameRepository requestStateFromWorker(String workerIp, int workerPort, int workerId, HealthCheckType type) {
        try (
                Socket socket = new Socket(workerIp, workerPort);
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream())
        ) {
            HealthCheckRequest request = new HealthCheckRequest(workerId, type, HealthCheckAction.REQUEST, null);
            Message<HealthCheckRequest> healthMsg = new Message<>(Sender.MASTER_HEALTH_CHECK_DAEMON, null, request, null);
            MessageWithWorkerMode msg = new MessageWithWorkerMode(healthMsg, null, null);

            out.writeObject(msg);
            out.flush();
            return (GameRepository) in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("[HealthCheck] Failed to recover state from " + workerIp);
            return null;
        }
    }

    private void sendStateToWorker(String workerIp, int workerPort, int workerId, HealthCheckType type, GameRepository state) {
        try (
                Socket socket = new Socket(workerIp, workerPort);
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream())
        ) {
            HealthCheckRequest request = new HealthCheckRequest(workerId, type, HealthCheckAction.UPDATE, state);
            Message<HealthCheckRequest> healthMsg = new Message<>(Sender.MASTER_HEALTH_CHECK_DAEMON, null, request, null);
            MessageWithWorkerMode msg = new MessageWithWorkerMode(healthMsg, null, null);

            out.writeObject(msg);
            out.flush();
            in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("[HealthCheck] Failed to send state to " + workerIp);
        }
    }

    private List<Integer> chooseBackupWorkers(int mainWorker, int noOfWorkers) {
        int noOfBackups = Integer.parseInt(prop.getProperty("noOfBackupWorkersPerGame"));

        List<Integer> backups = new ArrayList<>();
        for (int i = 1; i <= noOfBackups; i++) {
            int backup = Math.floorMod((mainWorker - 1) + i, noOfWorkers) + 1;
            backups.add(backup);
        }
        return backups;
    }

    public static boolean checkAlive(String workerIp, int healthPort, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(workerIp, healthPort), timeoutMs);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}