import java.util.Scanner;

public class Main {

    public static void main(String[] args) {

        try (Scanner scanner = new Scanner(System.in)) {

            System.out.println("=================================");
            System.out.println("        Welcome to EasyBet       ");
            System.out.println("=================================");

            while (true) {
                int role = askUserRole(scanner);

                switch (role) {
                    case 1:
                        System.out.println("Connecting as Manager...");
                        new Manager(scanner).start();
                        break;

                    case 2:
                        System.out.println("Connecting as Player...");
                        new Player(scanner).start();
                        break;

                    default:
                        System.out.println("Unknown option.");
                }
            }
        }
    }

    private static int askUserRole(Scanner scanner) {
        while (true) {
            System.out.println("\nChoose connection type:");
            System.out.println("1 → Manager");
            System.out.println("2 → Player");
            System.out.print("Enter your choice: ");

            if (scanner.hasNextInt()) {
                int level = scanner.nextInt();
                scanner.nextLine();
                if (level == 1 || level == 2) return level;
            } else {
                scanner.nextLine();
            }

            System.out.println("Invalid input. Please enter 1 or 2.");
        }
    }
}
