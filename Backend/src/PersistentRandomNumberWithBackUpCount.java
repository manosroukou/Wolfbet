public class PersistentRandomNumberWithBackUpCount {
    private int persistentRandomNumber;
    private int noOfBackUpsVisited;

    public PersistentRandomNumberWithBackUpCount(int persistentRandomNumber) {
        this.persistentRandomNumber = persistentRandomNumber;
        this.noOfBackUpsVisited = 0;
    }


    public synchronized int getPersistentRandomNumber() {
        return persistentRandomNumber;
    }

    public synchronized int getNoOfBackUpsVisited() {
        return noOfBackUpsVisited;
    }

    public synchronized void setPersistentRandomNumber(int persistentRandomNumber) {
        this.persistentRandomNumber = persistentRandomNumber ;
    }

    public synchronized void updateNoOfBackUpsVisited() {
        this.noOfBackUpsVisited += 1;
    }
}
