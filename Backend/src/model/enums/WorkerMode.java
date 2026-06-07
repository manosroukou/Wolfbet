package model.enums;

public enum WorkerMode {
    MAIN, // for the main worker
    LEADER, // for the worker that will now do the job of main, because main is down
    BACKUP, // for workers that will only receive the new data and will not respond
}
