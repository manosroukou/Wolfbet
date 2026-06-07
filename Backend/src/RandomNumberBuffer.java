import java.util.Queue;
import java.util.LinkedList;

public class RandomNumberBuffer {
    private final Queue<Integer> queue = new LinkedList<>();
    private final int capacity;
    private boolean isEnabled;

    // Used for active replication.
    // The first element of the queue is temporarily stored here.
    // Back up workers replay the game with this value
    private Integer persistentTopNumber;

    public RandomNumberBuffer(int capacity) {
        this.capacity = capacity;
        this.isEnabled = true;
    }

    public synchronized void put(int randomNumber) throws InterruptedException {
        while (queue.size() == capacity || !isEnabled) {
            wait();
        }

        queue.add(randomNumber);
        notify();   // single producer per buffer, no need for notifyAll
    }

    public synchronized Integer take() throws InterruptedException {
        while (queue.isEmpty()) {
            wait();
        }

        Integer randomNumber = queue.remove();
        notifyAll();
        return randomNumber;
    }

    public synchronized void disableBuffer() {
        this.isEnabled = false;
        notifyAll();
    }

    public synchronized void enableBuffer() {
        this.isEnabled = true;
        notifyAll();
    }

    public Integer getPersistentTopNumber() {
        return persistentTopNumber;
    }

    public void setPersistentTopNumber(int persistentTopNumber) {
        this.persistentTopNumber = persistentTopNumber;
    }
}