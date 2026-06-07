package model.enums;

public enum HealthCheckAction {
    REQUEST,  // master wants data from this worker
    UPDATE    // master is sending data to this worker
}