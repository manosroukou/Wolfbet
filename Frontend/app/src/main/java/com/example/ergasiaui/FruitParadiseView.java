package com.example.ergasiaui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import model.enums.PlayGameResultType;

/**
 * Fruit Paradise — 3-reel slot machine.
 *
 * Reels spin SEQUENTIALLY (reel 0 → finishes → reel 1 → finishes → reel 2)
 * so that we have full control of what each reel lands on.
 *
 * Multiplier → Payline mapping:
 *   20x   JACKPOT       → 3 identical fruits (any)
 *   3.5x  WIN           → 2x🍒 + 1 other (not 🍒)
 *   2.5x  WIN           → 2x🍌 + 1 other (not 🍌)
 *   1.5x  WIN           → 2x🍅 + 1 other (not 🍅)
 *   1x    BREAK_EVEN    → 1x🍒 + 2 different others (no accidental pair of 🍌/🍅/🍒)
 *   0.5x  PARTIAL_LOSS  → 1x🍌 + 2 different others (no accidental pair of 🍒/🍌/🍅)
 *   0x    LOSS           → 3 different fruits, none forming any pattern above
 *
 * Each reel tracks only an integer currentIndex (which fruit is on the payline).
 * Animation scrolls whole cells; no floating-point drift.
 */
public class FruitParadiseView extends View implements AnimatedGameView {

    // ── Fruit symbols ───────────────────────────────────────────────────
    // Indices: 0=🍒  1=🍌  2=🍅  3=🍇  4=🍉  5=🍍  6=🥝  7=🍓
    private static final String[] FRUITS = {
            "🍒", "🍌", "🍅", "🍇", "🍉", "🍍", "🥝", "🍓"
    };
    private static final int FRUIT_COUNT = FRUITS.length;

    // Special fruit indices used in payline rules
    private static final int CHERRY  = 0;  // 🍒
    private static final int BANANA  = 1;  // 🍌
    private static final int TOMATO  = 2;  // 🍅

    // "Safe" fruits = those that are NOT cherry, banana, or tomato.
    // Used for LOSS and for the "other" slots.
    private static final int[] SAFE_FRUITS = {3, 4, 5, 6, 7}; // 🍇🍉🍍🥝🍓

    private static final int REEL_COUNT = 3;
    private static final int VISIBLE_ROWS = 3;

    // ── Paints ──────────────────────────────────────────────────────────
    private final Paint fruitPaint      = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint framePaint      = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint frameStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dividerPaint    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlightPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint         = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint      = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Random random = new Random();

    // ── Per-reel state ──────────────────────────────────────────────────
    // currentIndex[i] = which fruit is currently on the payline for reel i (0..7)
    private final int[] currentIndex = new int[REEL_COUNT];

    // Animation offset in pixels (used only during animation)
    private final float[] animOffset = new float[REEL_COUNT];
    // How many whole cells we are animating (set at start, used for drawing)
    private final int[] animTotalCells = new int[REEL_COUNT];
    // Whether each reel is currently animating
    private final boolean[] reelAnimating = new boolean[REEL_COUNT];

    // ── Spin state ──────────────────────────────────────────────────────
    private boolean spinning = false;
    // The 3 fruit indices that the payline should show after spin
    private final int[] targetFruits = new int[REEL_COUNT];

    // ── Dimensions ──────────────────────────────────────────────────────
    private float reelWidth, reelHeight, reelLeft, reelTop, cellHeight;
    private float dividerX1, dividerX2;
    private final RectF machineRect  = new RectF();
    private final RectF reelAreaRect = new RectF();

    // ── Callbacks ───────────────────────────────────────────────────────
    private Runnable animationFinishedCallback;

    // ── Constructors ────────────────────────────────────────────────────
    public FruitParadiseView(Context context) { super(context); init(); }
    public FruitParadiseView(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public FruitParadiseView(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); init(); }

    private void init() {
        framePaint.setColor(Color.parseColor("#1B1B2F"));
        framePaint.setStyle(Paint.Style.FILL);

        frameStrokePaint.setColor(Color.parseColor("#FFD700"));
        frameStrokePaint.setStyle(Paint.Style.STROKE);
        frameStrokePaint.setStrokeWidth(4f);

        bgPaint.setColor(Color.parseColor("#FAFAFA"));

        dividerPaint.setColor(Color.parseColor("#CCCCCC"));
        dividerPaint.setStrokeWidth(3f);

        highlightPaint.setColor(Color.parseColor("#33FFD700"));
        highlightPaint.setStyle(Paint.Style.FILL);

        fruitPaint.setTextAlign(Paint.Align.CENTER);

        labelPaint.setColor(Color.parseColor("#FFD700"));
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTypeface(Typeface.DEFAULT_BOLD);

        // Start each reel at a random fruit
        for (int i = 0; i < REEL_COUNT; i++) {
            currentIndex[i] = random.nextInt(FRUIT_COUNT);
            animOffset[i] = 0f;
            animTotalCells[i] = 0;
            reelAnimating[i] = false;
        }
    }

    // ── Layout ──────────────────────────────────────────────────────────
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        float padding = w * 0.08f;
        float topReserve = h * 0.10f;

        machineRect.set(padding, topReserve, w - padding, h - padding * 0.5f);

        float innerPad = machineRect.width() * 0.06f;
        float innerTop = machineRect.top + innerPad * 1.8f;
        float innerBottom = machineRect.bottom - innerPad;
        reelAreaRect.set(
                machineRect.left + innerPad, innerTop,
                machineRect.right - innerPad, innerBottom
        );

        reelWidth  = reelAreaRect.width() / REEL_COUNT;
        reelHeight = reelAreaRect.height();
        reelLeft   = reelAreaRect.left;
        reelTop    = reelAreaRect.top;
        cellHeight = reelHeight / VISIBLE_ROWS;

        dividerX1 = reelLeft + reelWidth;
        dividerX2 = reelLeft + reelWidth * 2;

        fruitPaint.setTextSize(cellHeight * 0.55f);
        labelPaint.setTextSize(machineRect.width() * 0.07f);
    }

    // ── Drawing ─────────────────────────────────────────────────────────
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getWidth() == 0 || getHeight() == 0) return;

        float cornerRadius = machineRect.width() * 0.06f;

        // Machine frame
        canvas.drawRoundRect(machineRect, cornerRadius, cornerRadius, framePaint);
        canvas.drawRoundRect(machineRect, cornerRadius, cornerRadius, frameStrokePaint);

        // Title
        float titleY = machineRect.top + labelPaint.getTextSize() * 1.2f;
        canvas.drawText("🍒 FRUIT PARADISE 🍒", machineRect.centerX(), titleY, labelPaint);

        // Reel background
        float reelCorner = cornerRadius * 0.5f;
        canvas.drawRoundRect(reelAreaRect, reelCorner, reelCorner, bgPaint);

        // Clip to reel area
        canvas.save();
        canvas.clipRect(reelAreaRect);

        for (int reel = 0; reel < REEL_COUNT; reel++) {
            drawReel(canvas, reel);
        }

        // Payline highlight (middle row)
        float highlightTop = reelTop + cellHeight;
        float highlightBottom = highlightTop + cellHeight;
        canvas.drawRect(reelAreaRect.left, highlightTop,
                reelAreaRect.right, highlightBottom, highlightPaint);

        // Dividers
        canvas.drawLine(dividerX1, reelAreaRect.top, dividerX1, reelAreaRect.bottom, dividerPaint);
        canvas.drawLine(dividerX2, reelAreaRect.top, dividerX2, reelAreaRect.bottom, dividerPaint);

        canvas.restore();

        // Edge fades
        drawEdgeFade(canvas);

        // Payline arrows
        drawPaylineMarkers(canvas);

        // Reel border
        Paint reelBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
        reelBorder.setColor(Color.parseColor("#888888"));
        reelBorder.setStyle(Paint.Style.STROKE);
        reelBorder.setStrokeWidth(2f);
        canvas.drawRoundRect(reelAreaRect, reelCorner, reelCorner, reelBorder);
    }

    /**
     * Draw a single reel.
     *
     * When NOT animating: the payline (middle row, row index 1) shows currentIndex[reel].
     *   row 0 (top)    = currentIndex - 1
     *   row 1 (payline) = currentIndex
     *   row 2 (bottom)  = currentIndex + 1
     *
     * When animating: we scroll through animTotalCells cells.
     *   animOffset goes from 0 to animTotalCells * cellHeight.
     *   We compute which cell is at the top based on the offset.
     */
    private void drawReel(Canvas canvas, int reel) {
        float left = reelLeft + reel * reelWidth;
        float centerX = left + reelWidth / 2f;

        if (!reelAnimating[reel]) {
            // Static: draw 3 visible rows centered on currentIndex
            for (int row = -1; row <= VISIBLE_ROWS; row++) {
                int fruitIdx = wrapIndex(currentIndex[reel] + row - 1);
                float y = reelTop + row * cellHeight;
                float textY = y + cellHeight / 2f + fruitPaint.getTextSize() * 0.35f;
                canvas.drawText(FRUITS[fruitIdx], centerX, textY, fruitPaint);
            }
        } else {
            // Animating: offset scrolls upward
            float offset = animOffset[reel];
            // How many whole cells have scrolled past
            int cellsScrolled = (int) (offset / cellHeight);
            float fractional = offset - cellsScrolled * cellHeight;

            // Draw enough rows to cover visible area + 1 extra on each side
            for (int row = -1; row <= VISIBLE_ROWS; row++) {
                // The fruit that would be at this row position
                // Start from currentIndex-1 (the top row when offset=0)
                // and add cellsScrolled to account for scrolling
                int fruitIdx = wrapIndex(currentIndex[reel] - 1 + cellsScrolled + row);
                float y = reelTop + row * cellHeight - fractional;
                float textY = y + cellHeight / 2f + fruitPaint.getTextSize() * 0.35f;
                canvas.drawText(FRUITS[fruitIdx], centerX, textY, fruitPaint);
            }
        }
    }

    private void drawEdgeFade(Canvas canvas) {
        float fadeHeight = cellHeight * 0.7f;

        Paint topFade = new Paint();
        topFade.setShader(new LinearGradient(
                0, reelAreaRect.top, 0, reelAreaRect.top + fadeHeight,
                Color.parseColor("#FAFAFA"), Color.TRANSPARENT,
                Shader.TileMode.CLAMP));
        canvas.drawRect(reelAreaRect.left, reelAreaRect.top,
                reelAreaRect.right, reelAreaRect.top + fadeHeight, topFade);

        Paint bottomFade = new Paint();
        bottomFade.setShader(new LinearGradient(
                0, reelAreaRect.bottom - fadeHeight, 0, reelAreaRect.bottom,
                Color.TRANSPARENT, Color.parseColor("#FAFAFA"),
                Shader.TileMode.CLAMP));
        canvas.drawRect(reelAreaRect.left, reelAreaRect.bottom - fadeHeight,
                reelAreaRect.right, reelAreaRect.bottom, bottomFade);
    }

    private void drawPaylineMarkers(Canvas canvas) {
        float markerSize = reelAreaRect.width() * 0.03f;
        float centerY = reelTop + cellHeight * 1.5f;

        Paint arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        arrowPaint.setColor(Color.parseColor("#FF4444"));
        arrowPaint.setStyle(Paint.Style.FILL);

        Path leftArrow = new Path();
        float lx = reelAreaRect.left - markerSize * 0.5f;
        leftArrow.moveTo(lx, centerY - markerSize);
        leftArrow.lineTo(lx + markerSize * 1.5f, centerY);
        leftArrow.lineTo(lx, centerY + markerSize);
        leftArrow.close();
        canvas.drawPath(leftArrow, arrowPaint);

        Path rightArrow = new Path();
        float rx = reelAreaRect.right + markerSize * 0.5f;
        rightArrow.moveTo(rx, centerY - markerSize);
        rightArrow.lineTo(rx - markerSize * 1.5f, centerY);
        rightArrow.lineTo(rx, centerY + markerSize);
        rightArrow.close();
        canvas.drawPath(rightArrow, arrowPaint);
    }

    // ── Fruit selection logic ───────────────────────────────────────────

    /**
     * Determine the 3 payline fruits based on the multiplier.
     * Returns int[3] — fruit indices for reels 0, 1, 2.
     */
    private int[] computePaylineFruits(float multiplier) {
        if (multiplier == 20f) {
            return makeJackpot();
        } else if (multiplier == 3.5f) {
            return makePairWithSpecial(CHERRY);
        } else if (multiplier == 2.5f) {
            return makePairWithSpecial(BANANA);
        } else if (multiplier == 1.5f) {
            return makePairWithSpecial(TOMATO);
        } else if (multiplier == 1f) {
            return makeSingleSpecial(CHERRY);
        } else if (multiplier == 0.5f) {
            return makeSingleSpecial(BANANA);
        } else {
            // 0x LOSS
            return makeLoss();
        }
    }

    /** JACKPOT (20x): 3 identical fruits. Pick any fruit at random. */
    private int[] makeJackpot() {
        int f = random.nextInt(FRUIT_COUNT);
        return new int[]{f, f, f};
    }

    /**
     * WIN (3.5x / 2.5x / 1.5x): 2 of the special fruit + 1 different.
     * The "other" fruit must NOT be the special fruit.
     * Place the pair and the odd one in random positions.
     */
    private int[] makePairWithSpecial(int specialFruit) {
        // Pick the "other" fruit — anything except the special
        int other = pickRandomExcluding(FRUIT_COUNT, specialFruit);

        // 3 slots, the odd one goes in a random position
        int[] result = new int[]{specialFruit, specialFruit, specialFruit};
        int oddPosition = random.nextInt(3);
        result[oddPosition] = other;
        return result;
    }

    /**
     * BREAK_EVEN (1x) or PARTIAL_LOSS (0.5x):
     * Exactly 1 of the special fruit + 2 others.
     * The 2 others must:
     *   - Not be the special fruit
     *   - Not be the same as each other
     *   - Not both be cherry, banana, or tomato in a way that forms
     *     an accidental pair pattern (i.e., not 2x🍒, 2x🍌, 2x🍅)
     *
     * Safest approach: pick the 2 others from SAFE_FRUITS (🍇🍉🍍🥝🍓)
     * and ensure they are different.
     */
    private int[] makeSingleSpecial(int specialFruit) {
        // Pick 2 different safe fruits
        int safe1 = SAFE_FRUITS[random.nextInt(SAFE_FRUITS.length)];
        int safe2;
        do {
            safe2 = SAFE_FRUITS[random.nextInt(SAFE_FRUITS.length)];
        } while (safe2 == safe1);

        // Place the special in a random position
        int[] result = new int[3];
        int specialPos = random.nextInt(3);
        int otherIdx = 0;
        for (int i = 0; i < 3; i++) {
            if (i == specialPos) {
                result[i] = specialFruit;
            } else {
                result[i] = (otherIdx == 0) ? safe1 : safe2;
                otherIdx++;
            }
        }
        return result;
    }

    /**
     * LOSS (0x): 3 different fruits, no accidental patterns.
     * Rules:
     *   - All 3 must be different
     *   - No pair of 🍒🍒, 🍌🍌, or 🍅🍅
     *   - No triple of anything
     *
     * Safest: pick 3 different fruits from SAFE_FRUITS only.
     * This guarantees no cherry/banana/tomato appear at all.
     */
    private int[] makeLoss() {
        // Shuffle SAFE_FRUITS and pick first 3
        List<Integer> pool = new ArrayList<>();
        for (int f : SAFE_FRUITS) pool.add(f);
        java.util.Collections.shuffle(pool, random);
        return new int[]{pool.get(0), pool.get(1), pool.get(2)};
    }

    /** Pick a random fruit index [0, count) excluding one value. */
    private int pickRandomExcluding(int count, int exclude) {
        int result;
        do {
            result = random.nextInt(count);
        } while (result == exclude);
        return result;
    }

    // ── Animation ───────────────────────────────────────────────────────

    /**
     * Start a spin targeting specific fruits on each reel.
     * Reels spin SEQUENTIALLY: 0 → 1 → 2.
     */
    private void startSpin(int[] fruits) {
        if (spinning) return;
        if (getWidth() == 0 || getHeight() == 0) {
            post(() -> startSpin(fruits));
            return;
        }

        spinning = true;
        targetFruits[0] = fruits[0];
        targetFruits[1] = fruits[1];
        targetFruits[2] = fruits[2];

        android.util.Log.d("FRUIT_SPIN",
                "Target payline: " + FRUITS[fruits[0]] + " " + FRUITS[fruits[1]] + " " + FRUITS[fruits[2]]);

        // Start reel 0; its completion triggers reel 1, etc.
        animateReel(0);
    }

    /**
     * Animate a single reel to land on targetFruits[reelIndex].
     *
     * The number of cells to scroll:
     *   - We want the payline to show targetFruits[reel].
     *   - Currently the payline shows currentIndex[reel].
     *   - We need to scroll (target - current) cells, plus N full loops for visual effect.
     *   - Full loops = (3 + reelIndex) * FRUIT_COUNT for stagger effect.
     *
     * The animation is split into two phases:
     *   Phase 1 (60% duration): fast linear spin
     *   Phase 2 (40% duration): decelerate to exact position
     */
    private void animateReel(int reelIndex) {
        int current = currentIndex[reelIndex];
        int target  = targetFruits[reelIndex];

        // Calculate cells to scroll
        int loops = (3 + reelIndex) * FRUIT_COUNT;  // 24, 32, 40 cells of "spinning"
        int diff  = ((target - current) % FRUIT_COUNT + FRUIT_COUNT) % FRUIT_COUNT;
        int totalCells = loops + diff;

        animTotalCells[reelIndex] = totalCells;
        animOffset[reelIndex] = 0f;
        reelAnimating[reelIndex] = true;

        float totalPixels = totalCells * cellHeight;
        long duration = 1200 + reelIndex * 500L;  // 1200ms, 1700ms, 2200ms

        // Phase 1: fast spin (60% of distance)
        float phase1Pixels = totalPixels * 0.6f;
        long phase1Duration = (long) (duration * 0.45f);

        // Phase 2: decelerate to exact landing (40% of distance)
        float phase2Pixels = totalPixels - phase1Pixels;
        long phase2Duration = duration - phase1Duration;

        ValueAnimator fast = ValueAnimator.ofFloat(0f, phase1Pixels);
        fast.setDuration(phase1Duration);
        fast.setInterpolator(new LinearInterpolator());
        fast.addUpdateListener(a -> {
            animOffset[reelIndex] = (float) a.getAnimatedValue();
            invalidate();
        });

        ValueAnimator slow = ValueAnimator.ofFloat(0f, phase2Pixels);
        slow.setDuration(phase2Duration);
        slow.setInterpolator(new DecelerateInterpolator(2.5f));
        slow.addUpdateListener(a -> {
            animOffset[reelIndex] = phase1Pixels + (float) a.getAnimatedValue();
            invalidate();
        });

        slow.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // Snap to exact position
                reelAnimating[reelIndex] = false;
                currentIndex[reelIndex] = target;
                animOffset[reelIndex] = 0f;
                invalidate();

                android.util.Log.d("FRUIT_SPIN",
                        "Reel " + reelIndex + " landed on " + FRUITS[target]);

                // Chain: start next reel or finish
                int nextReel = reelIndex + 1;
                if (nextReel < REEL_COUNT) {
                    animateReel(nextReel);
                } else {
                    // All reels done
                    spinning = false;
                    android.util.Log.d("FRUIT_SPIN",
                            "Final payline: " + FRUITS[currentIndex[0]] + " "
                                    + FRUITS[currentIndex[1]] + " " + FRUITS[currentIndex[2]]);
                    if (animationFinishedCallback != null) {
                        animationFinishedCallback.run();
                    }
                }
            }
        });

        fast.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                slow.start();
            }
        });

        fast.start();
    }

    // ── AnimatedGameView interface ──────────────────────────────────────

    @Override
    public void playAnimation(PlayGameResultType resultType) {
        // Fallback: if called without multiplier, use a default
        float defaultMultiplier;
        switch (resultType) {
            case JACKPOT:      defaultMultiplier = 20f; break;
            case WIN:          defaultMultiplier = 3.5f; break;
            case BREAK_EVEN:   defaultMultiplier = 1f; break;
            case PARTIAL_LOSS: defaultMultiplier = 0.5f; break;
            case LOSS:
            default:           defaultMultiplier = 0f; break;
        }
        playAnimation(resultType, defaultMultiplier);
    }

    @Override
    public void playAnimation(PlayGameResultType resultType, float multiplier) {
        int[] fruits = computePaylineFruits(multiplier);
        startSpin(fruits);
    }

    @Override
    public void setOnAnimationFinished(Runnable callback) {
        this.animationFinishedCallback = callback;
    }

    // ── Utility ─────────────────────────────────────────────────────────
    private static int wrapIndex(int i) {
        return ((i % FRUIT_COUNT) + FRUIT_COUNT) % FRUIT_COUNT;
    }

    public boolean isSpinning() {
        return spinning;
    }

    public static String getFruitAt(int index) {
        return FRUITS[((index % FRUIT_COUNT) + FRUIT_COUNT) % FRUIT_COUNT];
    }
}