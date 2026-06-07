import model.enums.RiskLevel;
import serializables.Game;

// Proving that serialization and deserialization work
public class test {
    public static void main(String[] args) {

        try {
            Game demoGame = new Game(
                    "Book of Ra",
                    "Novomatic",
                    5,
                    1240,
                    "bookofra.png",
                    0.10f,
                    100.0f,
                    RiskLevel.HIGH,
                    "book_of_ra_123"
            );

            String json = JsonUtil.MAPPER.writeValueAsString(demoGame);

            System.out.println("JSON output:");
            System.out.println(json);

            Game parsedGame = JsonUtil.MAPPER.readValue(json, Game.class);

            System.out.println("\nParsed back to object:");
            System.out.println(parsedGame);

        } catch (Exception e) {
            e.printStackTrace();
        }


    }
}
