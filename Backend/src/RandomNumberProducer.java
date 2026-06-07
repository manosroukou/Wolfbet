import java.util.concurrent.ThreadLocalRandom;

public class RandomNumberProducer extends Thread {
    private final RandomNumberBuffer buffer;

    public RandomNumberProducer(RandomNumberBuffer buffer) {
        this.buffer = buffer;
    }

    @Override
    public void run() {
        try {
            while (true) {
                int randomNumber = ThreadLocalRandom.current().nextInt(0, 100);
                buffer.put(randomNumber);
                System.out.println("Produced: " + randomNumber);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}