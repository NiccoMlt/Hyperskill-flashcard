import flashcards.MainKt;
import org.hyperskill.hstest.stage.StageTest;

public abstract class FlashcardsTest<T> extends StageTest<T> {
    public FlashcardsTest() {
        super(MainKt.class);
    }
}
