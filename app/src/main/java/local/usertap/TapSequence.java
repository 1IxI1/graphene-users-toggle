package local.usertap;

/** Gesture recognition independent of Android, with monotonic millisecond timestamps. */
public final class TapSequence {
    public static final int NONE = 0;
    public static final int TRIPLE = 1;
    public static final int DRAG_DOWN = 2;
    private static final long MAX_PRESS = 250;
    private static final long MAX_GAP = 400;
    private static final long MAX_SEQUENCE = 1100;
    private final float slop;
    private final float radius;
    private int count;
    private long firstDown;
    private long lastUp;
    private long downAt;
    private float downX, downY, previousX, previousY;
    private boolean down;
    private boolean moved;

    public TapSequence(float slop, float radius) {
        this.slop = slop;
        this.radius = radius;
    }

    public void reset() {
        count = 0;
        down = false;
        moved = false;
    }

    public void down(float x, float y, long time) {
        if (count > 0 && (time < lastUp || time - lastUp > MAX_GAP
                || time - firstDown > MAX_SEQUENCE
                || distance(x, y, previousX, previousY) > radius)) count = 0;
        if (count == 0) firstDown = time;
        downAt = time;
        downX = x;
        downY = y;
        down = true;
        moved = false;
    }

    public void move(float x, float y) {
        if (down && distance(x, y, downX, downY) > slop) {
            moved = true;
            count = 0;
        }
    }

    public int up(float x, float y, long time) {
        if (!down) return NONE;
        move(x, y);
        down = false;
        if (moved) {
            boolean downward = y - downY > slop && y - downY > Math.abs(x - downX);
            count = 0;
            return downward ? DRAG_DOWN : NONE;
        }
        if (time < downAt || time - downAt > MAX_PRESS || time - firstDown > MAX_SEQUENCE) {
            count = 0;
            return NONE;
        }
        previousX = x;
        previousY = y;
        lastUp = time;
        count++;
        if (count == 3) {
            count = 0;
            return TRIPLE;
        }
        return NONE;
    }

    private static float distance(float x, float y, float a, float b) {
        return (float) Math.hypot(x - a, y - b);
    }
}
