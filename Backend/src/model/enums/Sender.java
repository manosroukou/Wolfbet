package model.enums;
import java.io.Serializable;

public enum Sender implements Serializable {
    MANAGER, PLAYER, MASTER_HEALTH_CHECK_DAEMON, WORKER, REDUCER;
    private static final long serialVersionUID = 1L;
}
