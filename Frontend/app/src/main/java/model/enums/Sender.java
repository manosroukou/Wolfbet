package model.enums;
import java.io.Serializable;

public enum Sender implements Serializable {
    MANAGER, PLAYER, WORKER, REDUCER;
    private static final long serialVersionUID = 1L;
}
