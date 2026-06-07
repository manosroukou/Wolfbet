import java.io.Serializable;

public class SRGResponse implements Serializable {
    // Marked as Integer and not int since it can be null
    private final Integer randomNumber;
    private final String sha256;
    private final String textMessage;


    public SRGResponse(Integer randomNumber, String sha256) {
        this.randomNumber = randomNumber;
        this.sha256 = sha256;
        this.textMessage = null;
    }

    public SRGResponse(String textMessage) {
        this.randomNumber = null;
        this.sha256 = null;
        this.textMessage = textMessage;
    }

    public String getTextMessage() {
        return textMessage;
    }

    public Integer getRandomNumber() {
        return randomNumber;
    }

    public String getSha256() {
        return sha256;
    }

}
