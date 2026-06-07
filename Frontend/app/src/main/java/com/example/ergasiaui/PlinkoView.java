package com.example.ergasiaui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import model.enums.PlayGameResultType;

/**
 * Plinko game view — ball drops through pegs into multiplier buckets.
 *
 * 8 rows of pegs, 14 symmetric buckets:
 *   20x 3.5x 2.5x 1.5x 1x 0.5x 0x 0x 0.5x 1x 1.5x 2.5x 3.5x 20x
 *
 * Every result type except LOSS (0x) has two possible buckets (left/right),
 * chosen randomly each time.
 *
 * Implements AnimatedGameView.
 */
public class PlinkoView extends View implements AnimatedGameView {

    // ── Configuration ───────────────────────────────────────────────────
    private static final int PEG_ROWS = 13;
    private static final int BUCKET_COUNT = 14;

    //  20 | 3.5 | 2.5 | 1.5 | 1 | 0.5 | 0 | 0 | 0.5 | 1 | 1.5 | 2.5 | 3.5 | 20
    private static final float[] MULTIPLIERS = {
            20f, 3.5f, 2.5f, 1.5f, 1f, 0.5f, 0f, 0f, 0.5f, 1f, 1.5f, 2.5f, 3.5f, 20f
    };

    private static final PlayGameResultType[] BUCKET_RESULTS = {
            PlayGameResultType.JACKPOT,       //  0 — 20x
            PlayGameResultType.WIN,           //  1 — 3.5x
            PlayGameResultType.WIN,           //  2 — 2.5x
            PlayGameResultType.WIN,           //  3 — 1.5x
            PlayGameResultType.BREAK_EVEN,    //  4 — 1x
            PlayGameResultType.PARTIAL_LOSS,  //  5 — 0.5x
            PlayGameResultType.LOSS,          //  6 — 0x
            PlayGameResultType.LOSS,          //  7 — 0x
            PlayGameResultType.PARTIAL_LOSS,  //  8 — 0.5x
            PlayGameResultType.BREAK_EVEN,    //  9 — 1x
            PlayGameResultType.WIN,           // 10 — 1.5x
            PlayGameResultType.WIN,           // 11 — 2.5x
            PlayGameResultType.WIN,           // 12 — 3.5x
            PlayGameResultType.JACKPOT,       // 13 — 20x
    };

    // Colors
    private static final int COL_BG      = Color.parseColor("#12121A");
    private static final int COL_PEG     = Color.parseColor("#556677");
    private static final int COL_BALL    = Color.parseColor("#F5C542");
    private static final int COL_LOSS    = Color.parseColor("#FF3355");
    private static final int COL_PARTIAL = Color.parseColor("#FF8800");
    private static final int COL_EVEN    = Color.parseColor("#FFCC00");
    private static final int COL_WIN     = Color.parseColor("#00CC66");
    private static final int COL_JACKPOT = Color.parseColor("#AA44FF");

    // ── Paints ──────────────────────────────────────────────────────────
    private final Paint paintPeg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintBall = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintBucket = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintGlow = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Random random = new Random();

    // Layout
    private float padTop, padBottom, padSide;
    private float rowSpacing, colSpacing;
    private float pegRadius, ballRadius;
    private float bucketHeight;

    // Ball state
    private float ballX = -1, ballY = -1;
    private boolean ballVisible = false;

    // Path & target
    private List<float[]> ballPath = new ArrayList<>();
    private int targetBucket = -1;
    private int flashBucket = -1;
    private float flashAlpha = 0f;

    // Animators
    private ValueAnimator dropAnimator;
    private ValueAnimator flashAnimator;

    // Callbacks
    private OnDropFinishedListener dropFinishedListener;
    private Runnable animationFinishedCallback;

    public interface OnDropFinishedListener {
        void onDropFinished(int bucketIndex, float multiplier, PlayGameResultType resultType);
    }

    public void setOnDropFinishedListener(OnDropFinishedListener listener) {
        this.dropFinishedListener = listener;
    }

    // ── Constructors ────────────────────────────────────────────────────
    public PlinkoView(Context context) {
        super(context);
        init();
    }

    public PlinkoView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PlinkoView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paintPeg.setColor(COL_PEG);
        paintBall.setColor(COL_BALL);
        paintBall.setShadowLayer(10f, 0f, 0f, COL_BALL);
        paintText.setColor(Color.WHITE);
        paintText.setTextAlign(Paint.Align.CENTER);
        paintText.setFakeBoldText(true);
        paintGlow.setStyle(Paint.Style.FILL);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    // ── Layout ──────────────────────────────────────────────────────────
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        computeLayout(w, h);
    }

    private void computeLayout(int w, int h) {
        padTop = h * 0.08f;
        padBottom = h * 0.15f;
        padSide = w * 0.03f;

        float playH = h - padTop - padBottom;
        rowSpacing = playH / (PEG_ROWS + 1);

        // Widest row (last row, row 12) has 15 pegs → 14 gaps.
        // colSpacing is the gap between adjacent pegs.
        int maxPegs = PEG_ROWS + 2; // 15
        colSpacing = (w - padSide * 2) / (maxPegs - 1);

        pegRadius = Math.max(3f, w * 0.008f);
        ballRadius = Math.max(6f, w * 0.018f);
        bucketHeight = padBottom * 0.8f;

        paintText.setTextSize(Math.max(11f, w * 0.032f));
    }

    // ── Peg positions ───────────────────────────────────────────────────
    private float pegX(int row, int col) {
        int pegsInRow = row + 3;
        float totalW = (pegsInRow - 1) * colSpacing;
        float startX = (getWidth() - totalW) / 2f;
        return startX + col * colSpacing;
    }

    private float pegY(int row) {
        return padTop + (row + 1) * rowSpacing;
    }

    // ── Bucket positions ────────────────────────────────────────────────
    // Buckets align with the gaps of the LAST peg row (row PEG_ROWS-1).
    // Last row has PEG_ROWS+2 pegs → PEG_ROWS+1 gaps.
    // But we have BUCKET_COUNT = PEG_ROWS+1 = 14 buckets, so 1-1.
    // Bucket i is centered between peg i and peg i+1 of the last row.

    private float lastRowPegX(int col) {
        int lastRow = PEG_ROWS - 1;
        int pegsInLastRow = lastRow + 3; // = 15
        float totalW = (pegsInLastRow - 1) * colSpacing;
        float startX = (getWidth() - totalW) / 2f;
        return startX + col * colSpacing;
    }

    private float bucketCenterX(int index) {
        // Center between peg[index] and peg[index+1] of the last row
        return (lastRowPegX(index) + lastRowPegX(index + 1)) / 2f;
    }

    private float bucketLeft(int index) {
        return bucketCenterX(index) - colSpacing / 2f;
    }

    private float bucketTop() {
        return getHeight() - padBottom;
    }

    // ── Path generation ─────────────────────────────────────────────────
    /**
     * 13 rows → 14 possible exit slots (0..13) = exactly 14 buckets.
     * Target bucket index == number of right-turns needed. No rounding.
     */
    private List<float[]> generatePath(int targetBucket) {
        int target = Math.max(0, Math.min(BUCKET_COUNT - 1, targetBucket));

        // target right-turns, rest left-turns, shuffled
        List<Boolean> decisions = new ArrayList<>();
        for (int i = 0; i < PEG_ROWS; i++) {
            decisions.add(i < target);
        }
        Collections.shuffle(decisions, random);

        // Build waypoints
        List<float[]> path = new ArrayList<>();
        path.add(new float[]{getWidth() / 2f, padTop * 0.3f});

        int rights = 0;
        for (int row = 0; row < PEG_ROWS; row++) {
            boolean right = decisions.get(row);
            int pegCol = rights + 1;

            float px = pegX(row, pegCol);
            float py = pegY(row);

            path.add(new float[]{px, py});

            if (right) rights++;

            float offX = right ? colSpacing * 0.4f : -colSpacing * 0.4f;
            path.add(new float[]{px + offX, py + rowSpacing * 0.35f});
        }

        path.add(new float[]{bucketCenterX(target), bucketTop() + bucketHeight * 0.4f});

        return path;
    }

    // ── Public API ──────────────────────────────────────────────────────
    public void drop(int bucketIndex) {
        if (getWidth() == 0 || getHeight() == 0) {
            post(() -> drop(bucketIndex));
            return;
        }

        cancelAnimations();

        targetBucket = Math.max(0, Math.min(BUCKET_COUNT - 1, bucketIndex));
        ballPath = generatePath(targetBucket);
        flashBucket = -1;
        flashAlpha = 0f;

        // Cumulative distances for interpolation
        float[] cumDist = new float[ballPath.size()];
        cumDist[0] = 0f;
        for (int i = 1; i < ballPath.size(); i++) {
            float dx = ballPath.get(i)[0] - ballPath.get(i - 1)[0];
            float dy = ballPath.get(i)[1] - ballPath.get(i - 1)[1];
            cumDist[i] = cumDist[i - 1] + (float) Math.sqrt(dx * dx + dy * dy);
        }
        float totalLen = cumDist[cumDist.length - 1];

        ballVisible = true;

        dropAnimator = ValueAnimator.ofFloat(0f, 1f);
        dropAnimator.setDuration(2500);
        dropAnimator.setInterpolator(input -> {
            if (input < 0.1f) return input * input * 10f;
            if (input > 0.85f) {
                float t = (input - 0.85f) / 0.15f;
                return 0.85f + 0.15f * (1f - (1f - t) * (1f - t));
            }
            return input;
        });

        dropAnimator.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();
            float dist = progress * totalLen;

            for (int i = 1; i < ballPath.size(); i++) {
                if (dist <= cumDist[i]) {
                    float segLen = cumDist[i] - cumDist[i - 1];
                    float t = segLen > 0 ? (dist - cumDist[i - 1]) / segLen : 0f;
                    ballX = ballPath.get(i - 1)[0] + t * (ballPath.get(i)[0] - ballPath.get(i - 1)[0]);
                    ballY = ballPath.get(i - 1)[1] + t * (ballPath.get(i)[1] - ballPath.get(i - 1)[1]);
                    break;
                }
            }
            invalidate();
        });

        dropAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                flashBucket = targetBucket;
                startFlashAnimation();

                if (dropFinishedListener != null) {
                    dropFinishedListener.onDropFinished(
                            targetBucket, MULTIPLIERS[targetBucket], BUCKET_RESULTS[targetBucket]);
                }
                if (animationFinishedCallback != null) {
                    animationFinishedCallback.run();
                }
            }
        });

        dropAnimator.start();
    }

    /**
     * Drop to a random bucket matching the result type.
     * Every type except LOSS has 2 options (left/right), picked randomly.
     * LOSS has 2 center buckets (indices 6, 7), also picked randomly.
     */
    public void dropToResult(PlayGameResultType resultType) {
        List<Integer> matching = new ArrayList<>();
        for (int i = 0; i < BUCKET_COUNT; i++) {
            if (BUCKET_RESULTS[i] == resultType) matching.add(i);
        }
        if (matching.isEmpty()) {
            drop(BUCKET_COUNT / 2);
        } else {
            drop(matching.get(random.nextInt(matching.size())));
        }
    }

    public void drop() {
        drop(random.nextInt(BUCKET_COUNT));
    }

    // ── AnimatedGameView ────────────────────────────────────────────────
    @Override
    public void playAnimation(PlayGameResultType resultType) {
        dropToResult(resultType);
    }

    @Override
    public void setOnAnimationFinished(Runnable callback) {
        this.animationFinishedCallback = callback;
    }

    // ── Flash animation ─────────────────────────────────────────────────
    private void startFlashAnimation() {
        flashAnimator = ValueAnimator.ofFloat(1f, 0f);
        flashAnimator.setDuration(600);
        flashAnimator.setRepeatCount(2);
        flashAnimator.setRepeatMode(ValueAnimator.REVERSE);
        flashAnimator.addUpdateListener(a -> {
            flashAlpha = (float) a.getAnimatedValue();
            invalidate();
        });
        flashAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                flashBucket = -1;
                flashAlpha = 0f;
                invalidate();
            }
        });
        flashAnimator.start();
    }

    private void cancelAnimations() {
        if (dropAnimator != null && dropAnimator.isRunning()) dropAnimator.cancel();
        if (flashAnimator != null && flashAnimator.isRunning()) flashAnimator.cancel();
        ballVisible = false;
        flashBucket = -1;
    }

    // ── Drawing ─────────────────────────────────────────────────────────
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;

        canvas.drawColor(COL_BG);

        Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        border.setStyle(Paint.Style.STROKE);
        border.setColor(Color.argb(30, 245, 197, 66));
        border.setStrokeWidth(2f);
        canvas.drawRoundRect(1, 1, w - 1, h - 1, 16f, 16f, border);

        drawPegs(canvas);
        drawBuckets(canvas);

        if (ballVisible && ballX >= 0 && ballY >= 0) {
            drawBall(canvas);
        }
    }

    private void drawPegs(Canvas canvas) {
        for (int row = 0; row < PEG_ROWS; row++) {
            int pegsInRow = row + 3;
            for (int col = 0; col < pegsInRow; col++) {
                float px = pegX(row, col);
                float py = pegY(row);

                if (ballVisible) {
                    float dx = ballX - px, dy = ballY - py;
                    float dist = (float) Math.sqrt(dx * dx + dy * dy);
                    if (dist < colSpacing) {
                        float a = 1f - dist / colSpacing;
                        paintGlow.setColor(Color.argb((int) (a * 50), 245, 197, 66));
                        canvas.drawCircle(px, py, pegRadius * 3f, paintGlow);
                    }
                }

                canvas.drawCircle(px, py, pegRadius, paintPeg);
            }
        }
    }

    private void drawBuckets(Canvas canvas) {
        float bTop = bucketTop();
        float bH = bucketHeight;

        for (int i = 0; i < BUCKET_COUNT; i++) {
            float left = bucketLeft(i) + 1f;
            float right = left + colSpacing - 2f;
            int color = getBucketColor(i);

            // Flash highlight
            if (flashBucket == i && flashAlpha > 0) {
                paintBucket.setColor(Color.argb((int) (flashAlpha * 100),
                        Color.red(color), Color.green(color), Color.blue(color)));
                canvas.drawRect(left, bTop, right, bTop + bH, paintBucket);
            }

            // Border
            Paint bp = new Paint(Paint.ANTI_ALIAS_FLAG);
            bp.setStyle(Paint.Style.STROKE);
            bp.setColor(Color.argb(120, Color.red(color), Color.green(color), Color.blue(color)));
            bp.setStrokeWidth(1.5f);
            canvas.drawRect(left, bTop, right, bTop + bH, bp);

            // Multiplier label
            paintText.setColor(color);
            String label = formatMultiplier(MULTIPLIERS[i]);
            float textY = bTop + bH / 2f + paintText.getTextSize() / 3f;
            canvas.drawText(label, (left + right) / 2f, textY, paintText);
        }
    }

    private void drawBall(Canvas canvas) {
        paintGlow.setColor(Color.argb(35, 245, 197, 66));
        canvas.drawCircle(ballX, ballY, ballRadius * 2.5f, paintGlow);

        paintBall.setColor(COL_BALL);
        canvas.drawCircle(ballX, ballY, ballRadius, paintBall);

        Paint hl = new Paint(Paint.ANTI_ALIAS_FLAG);
        hl.setColor(Color.argb(100, 255, 255, 255));
        canvas.drawCircle(ballX - ballRadius * 0.25f, ballY - ballRadius * 0.25f,
                ballRadius * 0.35f, hl);
    }

    // ── Helpers ──────────────────────────────────────────────────────────
    private int getBucketColor(int index) {
        switch (BUCKET_RESULTS[index]) {
            case JACKPOT:      return COL_JACKPOT;
            case WIN:          return COL_WIN;
            case BREAK_EVEN:   return COL_EVEN;
            case PARTIAL_LOSS: return COL_PARTIAL;
            case LOSS:
            default:           return COL_LOSS;
        }
    }

    private String formatMultiplier(float m) {
        if (m == 0f) return "0x";
        if (m == (int) m) return (int) m + "x";
        return m + "x";
    }

    // π.χ. PlayGameResult έχει getMultiplier() = 3.5f
    public void dropToMultiplier(float multiplier) {
        List<Integer> matching = new ArrayList<>();
        for (int i = 0; i < BUCKET_COUNT; i++) {
            if (MULTIPLIERS[i] == multiplier) matching.add(i);
        }
        drop(matching.get(random.nextInt(matching.size())));
    }

    @Override
    public void playAnimation(PlayGameResultType resultType, float multiplier) {
        dropToMultiplier(multiplier);
    }

    public static float getMultiplierAt(int i) {
        if (i < 0 || i >= BUCKET_COUNT) return 0f;
        return MULTIPLIERS[i];
    }

    public static PlayGameResultType getResultTypeAt(int i) {
        if (i < 0 || i >= BUCKET_COUNT) return PlayGameResultType.LOSS;
        return BUCKET_RESULTS[i];
    }

    public static int getBucketCount() { return BUCKET_COUNT; }
}