import model.enums.WorkerMode;
import serializables.Message;

import java.io.Serializable;

public record MessageWithWorkerMode(Message message, WorkerMode workerMode, Integer workerId) implements Serializable {}
