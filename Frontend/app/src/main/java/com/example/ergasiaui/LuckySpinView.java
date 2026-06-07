package com.example.ergasiaui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import model.enums.PlayGameResultType;

/**
 * Lucky Spin wheel with 11 segments mapped to multipliers.
 * Royal Casino edition.
 *
 * Segments (clockwise from top):
 *   Index:  0     1     2     3     4     5     6     7     8     9     10
 *   Multi: 0.0   0.0   0.0   0.1   0.5   1.0   1.1   1.3   2.0   2.5   10.0
 *
 * ResultType → Multiplier mapping:
 *   LOSS         → 0.0  (random among indices 0,1,2)
 *   PARTIAL_LOSS → 0.1 or 0.5  (chosen by multiplier param)
 *   BREAK_EVEN   → 1.0
 *   WIN          → 1.1, 1.3, 2.0, or 2.5  (chosen by multiplier param)
 *   JACKPOT      → 10.0
 */
public class LuckySpinView extends View implements AnimatedGameView {

    // ── Segment definitions (11 segments) ───────────────────────────────
    private static final int SEGMENT_COUNT = 11;
    private static final float SLICE_ANGLE = 360f / SEGMENT_COUNT;

    private static final float[] MULTIPLIERS = {
            0.0f, 0.0f, 0.0f, 0.1f, 0.5f, 1.0f, 1.1f, 1.3f, 2.0f, 2.5f, 10.0f
    };

    private static final String[] LABELS = {
            "x0",        // 0  - LOSS
            "x0",        // 1  - LOSS
            "x0",        // 2  - LOSS
            "x0.1",      // 3  - PARTIAL_LOSS
            "x0.5",      // 4  - PARTIAL_LOSS
            "x1.0",      // 5  - BREAK_EVEN
            "x1.1",      // 6  - WIN
            "x1.3",      // 7  - WIN
            "x2.0",      // 8  - WIN
            "x2.5",      // 9  - WIN
            "x10",       // 10 - JACKPOT
    };

    private static final PlayGameResultType[] SEGMENT_RESULTS = {
            PlayGameResultType.LOSS,          // 0
            PlayGameResultType.LOSS,          // 1
            PlayGameResultType.LOSS,          // 2
            PlayGameResultType.PARTIAL_LOSS,  // 3
            PlayGameResultType.PARTIAL_LOSS,  // 4
            PlayGameResultType.BREAK_EVEN,    // 5
            PlayGameResultType.WIN,           // 6
            PlayGameResultType.WIN,           // 7
            PlayGameResultType.WIN,           // 8
            PlayGameResultType.WIN,           // 9
            PlayGameResultType.JACKPOT,       // 10
    };

    // ── Colors per segment ──────────────────────────────────────────────
    private static final int[] COLORS = {
            Color.parseColor("#4A1A2E"),  // LOSS - dark burgundy
            Color.parseColor("#5C1E38"),  // LOSS - slightly lighter burgundy
            Color.parseColor("#4A1A2E"),  // LOSS - dark burgundy
            Color.parseColor("#8B5E3C"),  // PARTIAL - bronze
            Color.parseColor("#7A5030"),  // PARTIAL - darker bronze
            Color.parseColor("#2C3E6B"),  // EVEN - navy
            Color.parseColor("#1A6B3C"),  // WIN - emerald
            Color.parseColor("#1D7A44"),  // WIN - lighter emerald
            Color.parseColor("#1A6B3C"),  // WIN - emerald
            Color.parseColor("#1D7A44"),  // WIN - lighter emerald
            Color.parseColor("#B8860B"),  // JACKPOT - dark gold
    };

    // ── Emoji per segment ───────────────────────────────────────────────
    private static final String[] EMOJIS = {
            "💀", "💀", "💀",       // LOSS
            "😬", "😬",             // PARTIAL
            "🔄",                   // EVEN
            "⭐", "⭐", "⭐", "⭐", // WIN
            "💎",                   // JACKPOT
    };

    // ── Royal colors ────────────────────────────────────────────────────
    private static final int GOLD_BRIGHT = Color.parseColor("#FFD700");
    private static final int GOLD_DARK   = Color.parseColor("#B8860B");
    private static final int GOLD_LIGHT  = Color.parseColor("#FFF0AA");
    private static final int VELVET      = Color.parseColor("#1A0A2E");

    // ── Paints ──────────────────────────────────────────────────────────
    private final Paint slicePaint        = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint         = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint emojiPaint        = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint       = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint innerBorderPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerPaint       = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerRingPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerPaint       = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint titlePaint        = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sliceDividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF wheelRect = new RectF();
    private final Random random = new Random();

    // ── State ───────────────────────────────────────────────────────────
    private float currentAngle = 0f;
    private boolean spinning = false;

    // ── Callbacks ───────────────────────────────────────────────────────
    private Runnable animationFinishedCallback;

    // ── Constructors ────────────────────────────────────────────────────
    public LuckySpinView(Context context) { super(context); init(); }
    public LuckySpinView(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public LuckySpinView(Context context, AttributeSet attrs, int defStyle) { super(context, attrs, defStyle); init(); }

    private void init() {
        // Segment text (multiplier label)
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));

        // Emoji paint
        emojiPaint.setTextAlign(Paint.Align.CENTER);

        // Outer gold border
        borderPaint.setColor(GOLD_BRIGHT);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(10f);

        // Inner gold ring
        innerBorderPaint.setColor(GOLD_LIGHT);
        innerBorderPaint.setStyle(Paint.Style.STROKE);
        innerBorderPaint.setStrokeWidth(3f);

        // Center hub
        centerPaint.setStyle(Paint.Style.FILL);

        // Center ring
        centerRingPaint.setColor(GOLD_BRIGHT);
        centerRingPaint.setStyle(Paint.Style.STROKE);
        centerRingPaint.setStrokeWidth(4f);

        // Marker (pointer)
        markerPaint.setColor(GOLD_BRIGHT);
        markerPaint.setStyle(Paint.Style.FILL);

        markerStrokePaint.setColor(GOLD_DARK);
        markerStrokePaint.setStyle(Paint.Style.STROKE);
        markerStrokePaint.setStrokeWidth(2f);

        // Slice dividers
        sliceDividerPaint.setColor(GOLD_DARK);
        sliceDividerPaint.setStrokeWidth(2f);

        // Center title
        titlePaint.setColor(GOLD_BRIGHT);
        titlePaint.setTextAlign(Paint.Align.CENTER);
        titlePaint.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
    }

    // ── Drawing ─────────────────────────────────────────────────────────
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = Math.min(cx, cy) * 0.82f;

        wheelRect.set(cx - radius, cy - radius, cx + radius, cy + radius);

        // ── Gold border gradient ──
        borderPaint.setShader(new LinearGradient(
                cx - radius, cy - radius, cx + radius, cy + radius,
                new int[]{GOLD_LIGHT, GOLD_BRIGHT, GOLD_DARK, GOLD_BRIGHT, GOLD_LIGHT},
                null, Shader.TileMode.CLAMP
        ));

        canvas.save();
        canvas.rotate(currentAngle, cx, cy);

        // ── Draw segments ──
        for (int i = 0; i < SEGMENT_COUNT; i++) {
            slicePaint.setColor(COLORS[i]);
            float startAngle = i * SLICE_ANGLE - 90f;
            canvas.drawArc(wheelRect, startAngle, SLICE_ANGLE, true, slicePaint);
        }

        // ── Slice divider lines ──
        for (int i = 0; i < SEGMENT_COUNT; i++) {
            float angle = (float) Math.toRadians(i * SLICE_ANGLE - 90f);
            float x1 = cx + radius * 0.22f * (float) Math.cos(angle);
            float y1 = cy + radius * 0.22f * (float) Math.sin(angle);
            float x2 = cx + radius * (float) Math.cos(angle);
            float y2 = cy + radius * (float) Math.sin(angle);
            canvas.drawLine(x1, y1, x2, y2, sliceDividerPaint);
        }

        // ── Segment labels (emoji + multiplier) ──
        textPaint.setTextSize(radius * 0.08f);
        emojiPaint.setTextSize(radius * 0.07f);

        for (int i = 0; i < SEGMENT_COUNT; i++) {
            float midAngle = i * SLICE_ANGLE + SLICE_ANGLE / 2f;
            double radians = Math.toRadians(midAngle);

            // Emoji closer to the edge
            float emojiRadius = radius * 0.72f;
            float ex = cx + emojiRadius * (float) Math.sin(radians);
            float ey = cy - emojiRadius * (float) Math.cos(radians);

            canvas.save();
            canvas.rotate(midAngle, ex, ey);
            canvas.drawText(EMOJIS[i], ex, ey + emojiPaint.getTextSize() / 3f, emojiPaint);
            canvas.restore();

            // Multiplier text closer to center
            float textRadius = radius * 0.52f;
            float tx = cx + textRadius * (float) Math.sin(radians);
            float ty = cy - textRadius * (float) Math.cos(radians);

            canvas.save();
            canvas.rotate(midAngle, tx, ty);
            canvas.drawText(LABELS[i], tx, ty + textPaint.getTextSize() / 3f, textPaint);
            canvas.restore();
        }

        canvas.restore();

        // ── Outer gold border ──
        canvas.drawCircle(cx, cy, radius, borderPaint);

        // ── Inner gold ring ──
        canvas.drawCircle(cx, cy, radius - 14f, innerBorderPaint);

        // ── Decorative studs around border ──
        drawBorderStuds(canvas, cx, cy, radius);

        // ── Center hub ──
        float hubRadius = radius * 0.20f;

        centerPaint.setShader(new RadialGradient(
                cx, cy, hubRadius,
                new int[]{Color.parseColor("#2D1B4E"), VELVET},
                null, Shader.TileMode.CLAMP
        ));
        canvas.drawCircle(cx, cy, hubRadius, centerPaint);
        canvas.drawCircle(cx, cy, hubRadius, centerRingPaint);

        // Inner decorative ring
        Paint innerRing = new Paint(Paint.ANTI_ALIAS_FLAG);
        innerRing.setColor(GOLD_DARK);
        innerRing.setStyle(Paint.Style.STROKE);
        innerRing.setStrokeWidth(1.5f);
        canvas.drawCircle(cx, cy, hubRadius * 0.7f, innerRing);

        // "LUCKY SPIN" text
        titlePaint.setTextSize(radius * 0.08f);
        canvas.drawText("LUCKY", cx, cy - titlePaint.getTextSize() * 0.2f, titlePaint);
        canvas.drawText("SPIN", cx, cy + titlePaint.getTextSize() * 1.0f, titlePaint);

        // ── Pointer/marker ──
        drawMarker(canvas, cx, cy, radius);
    }

    private void drawBorderStuds(Canvas canvas, float cx, float cy, float radius) {
        Paint studPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        studPaint.setColor(GOLD_LIGHT);

        Paint studCenter = new Paint(Paint.ANTI_ALIAS_FLAG);
        studCenter.setColor(GOLD_DARK);

        int studCount = 22;
        float studRadius = radius * 0.025f;

        for (int i = 0; i < studCount; i++) {
            float angle = (float) Math.toRadians(i * (360f / studCount));
            float sx = cx + (radius - 7f) * (float) Math.cos(angle);
            float sy = cy + (radius - 7f) * (float) Math.sin(angle);
            canvas.drawCircle(sx, sy, studRadius, studPaint);
            canvas.drawCircle(sx, sy, studRadius * 0.4f, studCenter);
        }
    }

    private void drawMarker(Canvas canvas, float cx, float cy, float radius) {
        float markerSize = radius * 0.18f;

        Path marker = new Path();
        marker.moveTo(cx, cy - radius + markerSize * 0.55f);
        marker.lineTo(cx - markerSize / 2f, cy - radius - markerSize * 0.35f);
        marker.lineTo(cx + markerSize / 2f, cy - radius - markerSize * 0.35f);
        marker.close();

        // Shadow
        Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPaint.setColor(Color.parseColor("#60000000"));
        shadowPaint.setStyle(Paint.Style.FILL);
        canvas.save();
        canvas.translate(2f, 3f);
        canvas.drawPath(marker, shadowPaint);
        canvas.restore();

        // Gold fill
        markerPaint.setShader(new LinearGradient(
                cx - markerSize / 2f, cy - radius - markerSize * 0.35f,
                cx + markerSize / 2f, cy - radius + markerSize * 0.55f,
                GOLD_LIGHT, GOLD_DARK, Shader.TileMode.CLAMP
        ));
        canvas.drawPath(marker, markerPaint);
        canvas.drawPath(marker, markerStrokePaint);
    }

    // ── Spin targeting logic ────────────────────────────────────────────

    /**
     * Find the segment index that matches a given multiplier.
     * If multiple segments match (e.g. the three x0.0), pick one at random.
     */
    private int findSegmentForMultiplier(float multiplier) {
        List<Integer> matches = new ArrayList<>();
        float epsilon = 0.01f;
        for (int i = 0; i < SEGMENT_COUNT; i++) {
            if (Math.abs(MULTIPLIERS[i] - multiplier) < epsilon) {
                matches.add(i);
            }
        }
        if (matches.isEmpty()) {
            return random.nextInt(SEGMENT_COUNT);
        }
        return matches.get(random.nextInt(matches.size()));
    }

    /**
     * Find a random segment index that matches the given result type.
     */
    private int findSegmentForResultType(PlayGameResultType resultType) {
        List<Integer> matches = new ArrayList<>();
        for (int i = 0; i < SEGMENT_COUNT; i++) {
            if (SEGMENT_RESULTS[i] == resultType) {
                matches.add(i);
            }
        }
        if (matches.isEmpty()) {
            return random.nextInt(SEGMENT_COUNT);
        }
        return matches.get(random.nextInt(matches.size()));
    }

    // ── Spin animation ──────────────────────────────────────────────────

    /**
     * Spin the wheel to land on a specific segment index.
     *
     * The pointer is at the top (12 o'clock, i.e. -90° in canvas terms).
     * Segment i occupies the arc from (i * SLICE_ANGLE) to ((i+1) * SLICE_ANGLE),
     * measured clockwise from the top (before any rotation).
     *
     * When we rotate the wheel by angle A (clockwise), the pointer points at
     * the position that was at angle A from the top. So to land the pointer
     * in the CENTER of segment i, we need:
     *
     *   finalAngle mod 360 = i * SLICE_ANGLE + SLICE_ANGLE / 2
     *
     * We add 6 full rotations for visual effect, then compute the exact
     * remaining angle.
     */
    private ValueAnimator currentAnimator;
    private void spinToSegment(int targetIndex) {
        // Cancel any running animation
        if (currentAnimator != null && currentAnimator.isRunning()) {
            currentAnimator.cancel();
        }
        spinning = true;

        currentAngle = currentAngle % 360f;
        float startAngle = currentAngle;

        // The pointer is fixed at the top (12 o'clock).
        // Segment i has its center at (i * SLICE_ANGLE + SLICE_ANGLE / 2) on the wheel.
        // When the wheel rotates clockwise by A degrees, the pointer points at
        // position (360 - A) % 360 on the wheel's coordinate system.
        //
        // So we need:  (360 - finalAngle % 360) % 360 == segmentCenter
        //         i.e.  finalAngle % 360 == (360 - segmentCenter) % 360
        float segmentCenter = targetIndex * SLICE_ANGLE + SLICE_ANGLE / 2f;
        float targetAngle = (360f - segmentCenter) % 360f;

        // Total rotation = 6 full spins + remaining to reach target
        float baseRotation = 360f * 6;
        float remaining = (targetAngle - startAngle % 360f + 360f) % 360f;
        float totalRotation = baseRotation + remaining;

        android.util.Log.d("LUCKY_SPIN",
                "Spinning to segment " + targetIndex + " (" + LABELS[targetIndex]
                        + "), target angle=" + targetAngle
                        + ", total rotation=" + totalRotation);

        currentAnimator = ValueAnimator.ofFloat(0f, totalRotation);
        //ValueAnimator animator = ValueAnimator.ofFloat(0f, totalRotation);
        currentAnimator.setDuration(4500);
        currentAnimator.setInterpolator(new DecelerateInterpolator(1.8f));

        currentAnimator.addUpdateListener(animation -> {
            currentAngle = startAngle + (float) animation.getAnimatedValue();
            invalidate();
        });

        currentAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                spinning = false;
                android.util.Log.d("LUCKY_SPIN",
                        "Landed on segment " + targetIndex + " → " + LABELS[targetIndex]);
                if (animationFinishedCallback != null) {
                    animationFinishedCallback.run();
                }
            }
        });

        currentAnimator.start();
    }

    // ── AnimatedGameView interface ──────────────────────────────────────

    /**
     * Play animation for a result type without a specific multiplier.
     * Picks a default multiplier for the result type.
     */
    @Override
    public void playAnimation(PlayGameResultType resultType) {
        float defaultMultiplier;
        switch (resultType) {
            case JACKPOT:      defaultMultiplier = 10.0f; break;
            case WIN:          defaultMultiplier = 2.0f;  break;
            case BREAK_EVEN:   defaultMultiplier = 1.0f;  break;
            case PARTIAL_LOSS: defaultMultiplier = 0.5f;  break;
            case LOSS:
            default:           defaultMultiplier = 0.0f;  break;
        }
        playAnimation(resultType, defaultMultiplier);
    }

    /**
     * Play animation for a result type with a specific multiplier.
     * The multiplier determines exactly which segment to land on.
     */
    @Override
    public void playAnimation(PlayGameResultType resultType, float multiplier) {
        int targetIndex = findSegmentForMultiplier(multiplier);

        android.util.Log.d("LUCKY_SPIN",
                "playAnimation: resultType=" + resultType
                        + ", multiplier=" + multiplier
                        + ", targetSegment=" + targetIndex
                        + " (" + LABELS[targetIndex] + ")");

        spinToSegment(targetIndex);
    }

    @Override
    public void setOnAnimationFinished(Runnable callback) {
        this.animationFinishedCallback = callback;
    }

    // ── Public utility ──────────────────────────────────────────────────

    public boolean isSpinning() { return spinning; }

    public static float getMultiplierForSegment(int index) {
        return MULTIPLIERS[index % SEGMENT_COUNT];
    }

    public static PlayGameResultType getResultTypeForSegment(int index) {
        return SEGMENT_RESULTS[index % SEGMENT_COUNT];
    }
}