import model.enums.HealthCheckAction;
import model.enums.HealthCheckType;

import java.io.Serializable;

public record HealthCheckRequest(int workerId, HealthCheckType type, HealthCheckAction action, GameRepository state) implements Serializable {}
