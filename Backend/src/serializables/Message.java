package serializables;

import model.enums.*;
import java.io.Serializable;

public class Message<T> implements Serializable {
    private static final long serialVersionUID = 1L;
    private final Sender sender;
    private final ActionType action;
    private final T payload;
    private final String requestId;

    public Message(Sender sender, ActionType action, T payload, String requestId) {
        this.sender = sender;
        this.action = action;
        this.payload = payload;
        this.requestId = requestId;
    }

    public Sender getSender() { return sender; }
    public ActionType getAction() { return action; }
    public T getPayload() { return payload; }
    public String getRequestId() { return requestId; }

}