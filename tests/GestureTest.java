import local.usertap.TapSequence;

public final class GestureTest {
    private static int tap(TapSequence sequence, long at) {
        sequence.down(20, 20, at);
        return sequence.up(20, 20, at + 60);
    }
    private static void expect(int expected, int actual, String message) {
        if (expected != actual) throw new AssertionError(message + ": " + actual);
    }
    public static void main(String[] args) {
        TapSequence sequence = new TapSequence(8, 30);
        expect(0, tap(sequence, 0), "single");
        expect(0, tap(sequence, 150), "double");
        expect(TapSequence.TRIPLE, tap(sequence, 300), "triple");
        expect(0, tap(sequence, 450), "fourth tap does not repeat");
        sequence.reset();
        tap(sequence, 1000);
        tap(sequence, 1150);
        expect(0, tap(sequence, 1800), "old taps expire");
        sequence.reset();
        tap(sequence, 2000);
        tap(sequence, 2150);
        sequence.down(20, 20, 2300);
        expect(0, sequence.up(20, 20, 2700), "long press cancels");
        expect(0, tap(sequence, 2800), "long press clears sequence");
        sequence.reset();
        tap(sequence, 3000);
        tap(sequence, 3150);
        sequence.down(20, 20, 3300);
        sequence.move(20, 70);
        expect(TapSequence.DRAG_DOWN, sequence.up(20, 70, 3450), "drag opens shade");
        expect(0, tap(sequence, 3500), "drag clears sequence");
        sequence.reset();
        tap(sequence, 4000);
        tap(sequence, 4150);
        sequence.reset();
        expect(0, sequence.up(20, 20, 4300), "cancel or extra pointer cannot complete triple");
        tap(sequence, 4400);
        sequence.down(100, 20, 4550);
        expect(0, sequence.up(100, 20, 4610), "distant tap restarts sequence");
        expect(0, tap(sequence, 4700), "returning to old point is not a triple");
        System.out.println("PASS: triple taps, timing, cancellation and drags");
    }
}
