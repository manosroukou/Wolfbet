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
 * Dragon Fortune — 3-reel slot machine with animal symbols.
 *
 * Reels spin SEQUENTIALLY (reel 0 → finishes → reel 1 → finishes → reel 2)
 * so that we have full control of what each reel lands on.
 *
 * Multiplier → Payline mapping:
 *   40x   JACKPOT       → 3 identical animals (any)
 *   6.5x  WIN           → 2x🐉 + 1 other (not 🐉)
 *   2.0x  WIN           → 2x🦁 + 1 other (not 🦁)
 *   1.0x  BREAK_EVEN    → 1x🦁 + 2 different others (no accidental pair)
 *   0x    LOSS           → 3 different animals, none forming any pattern above
 *
 * PARTIAL_LOSS is not expected from the server for this game.
 * If received, it falls back to LOSS (0x).
 *
 * Each reel tracks only an integer currentIndex (which animal is on the payline).
 * Animation scrolls whole cells; no floating-point drift.
 */
public class DragonFortuneView extends View implements AnimatedGameView {

    // ── Animal symbols ──────────────────────────────────────────────────
    // Indices: 0=🐉  1=🦁  2=🐺  3=🐻  4=🦅  5=🐍  6=🦊  7=🐯
    private static final String[] ANIMALS = {
            "🐉", "🦁", "🐺", "🐻", "🦅", "🐍", "🦊", "🐯"
    };
    private static final int ANIMAL_COUNT = ANIMALS.length;

    // Special animal indices used in payline rules
    private static final int DRAGON = 0;  // 🐉
    private static final int LION   = 1;  // 🦁

    // "Safe" animals = those that are NOT dragon or lion.
    // Used for LOSS and for the "other" slots.
    private static final int[] SAFE_ANIMALS = {2, 3, 4, 5, 6, 7}; // 🐺🐻🦅🐍🦊🐯

    private static final int REEL_COUNT = 3;
    private static final int VISIBLE_ROWS = 3;

    // ── Paints ──────────────────────────────────────────────────────────
    private final Paint animalPaint      = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint framePaint       = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint frameStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dividerPaint     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlightPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint          = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint       = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Random random = new Random();

    // ── Per-reel state ──────────────────────────────────────────────────
    private final int[] currentIndex = new int[REEL_COUNT];
    private final float[] animOffset = new float[REEL_COUNT];
    private final int[] animTotalCells = new int[REEL_COUNT];
    private final boolean[] reelAnimating = new boolean[REEL_COUNT];

    // ── Spin state ──────────────────────────────────────────────────────
    private boolean spinning = false;
    private final int[] targetAnimals = new int[REEL_COUNT];

    // ── Dimensions ──────────────────────────────────────────────────────
    private float reelWidth, reelHeight, reelLeft, reelTop, cellHeight;
    private float dividerX1, dividerX2;
    private final RectF machineRect  = new RectF();
    private final RectF reelAreaRect = new RectF();

    // ── Callbacks ───────────────────────────────────────────────────────
    private Runnable animationFinishedCallback;

    // ── Constructors ────────────────────────────────────────────────────
    public DragonFortuneView(Context context) { super(context); init(); }
    public DragonFortuneView(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public DragonFortuneView(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); init(); }

    private void init() {
        framePaint.setColor(Color.parseColor("#1A0A2E"));
        framePaint.setStyle(Paint.Style.FILL);

        frameStrokePaint.setColor(Color.parseColor("#FFD700"));
        frameStrokePaint.setStyle(Paint.Style.STROKE);
        frameStrokePaint.setStrokeWidth(4f);

        bgPaint.setColor(Color.parseColor("#FAFAFA"));

        dividerPaint.setColor(Color.parseColor("#CCCCCC"));
        dividerPaint.setStrokeWidth(3f);

        highlightPaint.setColor(Color.parseColor("#33FFD700"));
        highlightPaint.setStyle(Paint.Style.FILL);

        animalPaint.setTextAlign(Paint.Align.CENTER);

        labelPaint.setColor(Color.parseColor("#FFD700"));
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTypeface(Typeface.DEFAULT_BOLD);

        for (int i = 0; i < REEL_COUNT; i++) {
            currentIndex[i] = random.nextInt(ANIMAL_COUNT);
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

        animalPaint.setTextSize(cellHeight * 0.55f);
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
        canvas.drawText("🐉 DRAGON FORTUNE 🐉", machineRect.centerX(), titleY, labelPaint);

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

    private void drawReel(Canvas canvas, int reel) {
        float left = reelLeft + reel * reelWidth;
        float centerX = left + reelWidth / 2f;

        if (!reelAnimating[reel]) {
            for (int row = -1; row <= VISIBLE_ROWS; row++) {
                int animalIdx = wrapIndex(currentIndex[reel] + row - 1);
                float y = reelTop + row * cellHeight;
                float textY = y + cellHeight / 2f + animalPaint.getTextSize() * 0.35f;
                canvas.drawText(ANIMALS[animalIdx], centerX, textY, animalPaint);
            }
        } else {
            float offset = animOffset[reel];
            int cellsScrolled = (int) (offset / cellHeight);
            float fractional = offset - cellsScrolled * cellHeight;

            for (int row = -1; row <= VISIBLE_ROWS; row++) {
                int animalIdx = wrapIndex(currentIndex[reel] - 1 + cellsScrolled + row);
                float y = reelTop + row * cellHeight - fractional;
                float textY = y + cellHeight / 2f + animalPaint.getTextSize() * 0.35f;
                canvas.drawText(ANIMALS[animalIdx], centerX, textY, animalPaint);
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

    // ── Animal selection logic ───────────────────────────────────────────

    /**
     * Determine the 3 payline animals based on the multiplier.
     *
     *   40x   JACKPOT     → 3 identical animals (any)
     *   6.5x  WIN         → 2x🐉 + 1 other (not 🐉)
     *   2.0x  WIN         → 2x🦁 + 1 other (not 🦁)
     *   1.0x  BREAK_EVEN  → 1x🦁 + 2 different safe others
     *   0x    LOSS        → 3 different safe animals
     */
    private int[] computePaylineAnimals(float multiplier) {
        if (multiplier == 40f) {
            return makeJackpot();
        } else if (multiplier == 6.5f) {
            return makePairWithSpecial(DRAGON);
        } else if (multiplier == 2.0f) {
            return makePairWithSpecial(LION);
        } else if (multiplier == 1.0f) {
            return makeSingleSpecial(LION);
        } else {
            // 0x LOSS (also fallback for unexpected multipliers)
            return makeLoss();
        }
    }

    /** JACKPOT (40x): 3 identical animals. Pick any animal at random. */
    private int[] makeJackpot() {
        int a = random.nextInt(ANIMAL_COUNT);
        return new int[]{a, a, a};
    }

    /**
     * WIN (6.5x / 2.0x): 2 of the special animal + 1 different.
     * The "other" animal must NOT be the special animal.
     * Place the pair and the odd one in random positions.
     */
    private int[] makePairWithSpecial(int specialAnimal) {
        int other = pickRandomExcluding(ANIMAL_COUNT, specialAnimal);

        int[] result = new int[]{specialAnimal, specialAnimal, specialAnimal};
        int oddPosition = random.nextInt(3);
        result[oddPosition] = other;
        return result;
    }

    /**
     * BREAK_EVEN (1.0x): Exactly 1 lion + 2 different safe others.
     * The 2 others must:
     *   - Not be dragon or lion
     *   - Not be the same as each other
     */
    private int[] makeSingleSpecial(int specialAnimal) {
        int safe1 = SAFE_ANIMALS[random.nextInt(SAFE_ANIMALS.length)];
        int safe2;
        do {
            safe2 = SAFE_ANIMALS[random.nextInt(SAFE_ANIMALS.length)];
        } while (safe2 == safe1);

        int[] result = new int[3];
        int specialPos = random.nextInt(3);
        int otherIdx = 0;
        for (int i = 0; i < 3; i++) {
            if (i == specialPos) {
                result[i] = specialAnimal;
            } else {
                result[i] = (otherIdx == 0) ? safe1 : safe2;
                otherIdx++;
            }
        }
        return result;
    }

    /**
     * LOSS (0x): 3 different safe animals, no accidental patterns.
     * Only picks from SAFE_ANIMALS so no dragon or lion appear.
     */
    private int[] makeLoss() {
        List<Integer> pool = new ArrayList<>();
        for (int a : SAFE_ANIMALS) pool.add(a);
        java.util.Collections.shuffle(pool, random);
        return new int[]{pool.get(0), pool.get(1), pool.get(2)};
    }

    private int pickRandomExcluding(int count, int exclude) {
        int result;
        do {
            result = random.nextInt(count);
        } while (result == exclude);
        return result;
    }

    // ── Animation ───────────────────────────────────────────────────────

    private void startSpin(int[] animals) {
        if (spinning) return;
        if (getWidth() == 0 || getHeight() == 0) {
            post(() -> startSpin(animals));
            return;
        }

        spinning = true;
        targetAnimals[0] = animals[0];
        targetAnimals[1] = animals[1];
        targetAnimals[2] = animals[2];

        android.util.Log.d("DRAGON_SPIN",
                "Target payline: " + ANIMALS[animals[0]] + " " + ANIMALS[animals[1]] + " " + ANIMALS[animals[2]]);

        animateReel(0);
    }

    private void animateReel(int reelIndex) {
        int current = currentIndex[reelIndex];
        int target  = targetAnimals[reelIndex];

        int loops = (3 + reelIndex) * ANIMAL_COUNT;
        int diff  = ((target - current) % ANIMAL_COUNT + ANIMAL_COUNT) % ANIMAL_COUNT;
        int totalCells = loops + diff;

        animTotalCells[reelIndex] = totalCells;
        animOffset[reelIndex] = 0f;
        reelAnimating[reelIndex] = true;

        float totalPixels = totalCells * cellHeight;
        long duration = 1200 + reelIndex * 500L;

        float phase1Pixels = totalPixels * 0.6f;
        long phase1Duration = (long) (duration * 0.45f);

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
                reelAnimating[reelIndex] = false;
                currentIndex[reelIndex] = target;
                animOffset[reelIndex] = 0f;
                invalidate();

                android.util.Log.d("DRAGON_SPIN",
                        "Reel " + reelIndex + " landed on " + ANIMALS[target]);

                int nextReel = reelIndex + 1;
                if (nextReel < REEL_COUNT) {
                    animateReel(nextReel);
                } else {
                    spinning = false;
                    android.util.Log.d("DRAGON_SPIN",
                            "Final payline: " + ANIMALS[currentIndex[0]] + " "
                                    + ANIMALS[currentIndex[1]] + " " + ANIMALS[currentIndex[2]]);
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
        float defaultMultiplier;
        switch (resultType) {
            case JACKPOT:      defaultMultiplier = 40f;  break;
            case WIN:          defaultMultiplier = 6.5f; break;
            case BREAK_EVEN:   defaultMultiplier = 1.0f; break;
            case PARTIAL_LOSS: defaultMultiplier = 0f;   break; // not expected, fallback to LOSS
            case LOSS:
            default:           defaultMultiplier = 0f;   break;
        }
        playAnimation(resultType, defaultMultiplier);
    }

    @Override
    public void playAnimation(PlayGameResultType resultType, float multiplier) {
        int[] animals = computePaylineAnimals(multiplier);
        startSpin(animals);
    }

    @Override
    public void setOnAnimationFinished(Runnable callback) {
        this.animationFinishedCallback = callback;
    }

    // ── Utility ─────────────────────────────────────────────────────────
    private static int wrapIndex(int i) {
        return ((i % ANIMAL_COUNT) + ANIMAL_COUNT) % ANIMAL_COUNT;
    }

    public boolean isSpinning() {
        return spinning;
    }

    public static String getAnimalAt(int index) {
        return ANIMALS[((index % ANIMAL_COUNT) + ANIMAL_COUNT) % ANIMAL_COUNT];
    }
}