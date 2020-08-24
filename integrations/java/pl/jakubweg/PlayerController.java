package pl.jakubweg;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;

@SuppressLint({"LongLogTag"})
public class PlayerController {
    public static final String TAG = "jakubweg.PlayerController";
    public static final boolean VERBOSE = true;
    @SuppressWarnings("PointlessBooleanExpression")
    public static final boolean VERBOSE_DRAW_OPTIONS = true && VERBOSE;

    private static final Timer sponsorTimer = new Timer("sponsor-skip-timer");
    public static WeakReference<Activity> playerActivity = new WeakReference<>(null);
    public static SponsorSegment[] sponsorSegmentsOfCurrentVideo;
    private static WeakReference<Object> currentPlayerController = new WeakReference<>(null);
    private static Method setMillisecondMethod;
    private static long allowNextSkipRequestTime = 0L;
    private static String currentVideoId;
    private static long currentVideoLength = 1L;
    private static long lastKnownVideoTime = -1L;
    private static final Runnable findAndSkipSegmentRunnable = new Runnable() {
        @Override
        public void run() {
//            Log.d(TAG, "findAndSkipSegmentRunnable");
            findAndSkipSegment(false);
        }
    };
    private static float sponsorBarLeft = 1f;
    private static float sponsorBarRight = 1f;
    private static float sponsorBarThickness = 2f;
    private static TimerTask skipSponsorTask = null;

    public static String getCurrentVideoId() {
        return currentVideoId;
    }

    public static void setCurrentVideoId(final String videoId) {
        if (videoId == null) {
            Log.d(TAG, "setCurrentVideoId: videoId is null");
            return;
        }

        if (!SponsorBlockSettings.isSponsorBlockEnabled) {
            currentVideoId = null;
            return;
        }

        if (Looper.myLooper() != Looper.getMainLooper()) // check if thread is not main
            return;

        if (videoId.equals(currentVideoId))
            return;

        currentVideoId = videoId;
        sponsorSegmentsOfCurrentVideo = null;
        if (VERBOSE)
            Log.d(TAG, "setCurrentVideoId: videoId=" + videoId);

        sponsorTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                executeDownloadSegments(currentVideoId, false);
            }
        }, 0);
    }

    /**
     * Called when creating some kind of youtube internal player controlled, every time when new video starts to play
     */
    public static void onCreate(final Object o) {
//        "Plugin.printStackTrace();

        if (o == null) {
            Log.e(TAG, "onCreate called with null object");
            return;
        }

        if (VERBOSE)
            Log.i(TAG, String.format("onCreate called with object %s on thread %s", o.toString(), Thread.currentThread().toString()));

        try {
            setMillisecondMethod = o.getClass().getMethod("a", Long.TYPE);
            setMillisecondMethod.setAccessible(true);

            lastKnownVideoTime = 0;
            currentVideoLength = 1;
            currentPlayerController = new WeakReference<>(o);

            SkipSegmentView.hide();
            NewSegmentHelperLayout.hide();

            // add image button when starting new video
            Activity activity = playerActivity.get();
            if (activity != null)
                SponsorBlockUtils.addImageButton(activity, 5);
        } catch (Exception e) {
            Log.e(TAG, "Exception while initializing skip method", e);
        }
    }

    public static void executeDownloadSegments(String videoId, boolean ignoreCache) {
        SponsorSegment[] segments = SponsorBlockUtils.getSegmentsForVideo(videoId, ignoreCache);
        Arrays.sort(segments);

        if (VERBOSE)
            for (SponsorSegment segment : segments) {
                Log.v(TAG, "Detected segment: " + segment.toString());
            }

        sponsorSegmentsOfCurrentVideo = segments;
//        new Handler(Looper.getMainLooper()).post(findAndSkipSegmentRunnable);
    }

    /**
     * Works in 14.x, waits some time of object to me filled with data,
     * No longer used, i've found another way to get faster videoId
     */
    @Deprecated
    public static void asyncGetVideoLinkFromObject(final Object o) {
        // code no longer used

        //        if (currentVideoLink != null) {
//            if (VERBOSE)
//                Log.w(TAG, "asyncGetVideoLinkFromObject: currentVideoLink != null probably share button was clicked");
//            return;
//        }
//
//        new Thread(new Runnable() {
//            @Override
//            public void run() {
//                try {
//                    // It used to be "b" in 14.x version, it's "a" in 15.x
//                    Field b = o.getClass().getDeclaredField("b");
//
//                    int attempts = 0;
//                    String videoUrl = null;
//                    while (true) {
//                        Object objLink = b.get(o);
//                        if (objLink == null) {
//                            if (VERBOSE)
//                                Log.e(TAG, "asyncGetVideoLinkFromObject: objLink is null");
//                        } else {
//                            videoUrl = objLink.toString();
//                            if (videoUrl.isEmpty())
//                                videoUrl = null;
//                        }
//
//                        if (videoUrl != null)
//                            break;
//
//                        if (attempts++ > 5) {
//                            Log.w(TAG, "asyncGetVideoLinkFromObject: attempts++ > 5");
//                            return;
//                        }
//                        Thread.sleep(50);
//                    }
//
//                    if (currentVideoLink == null) {
//                        currentVideoLink = videoUrl;
//                        if (VERBOSE)
//                            Log.d(TAG, "asyncGetVideoLinkFromObject: link set to " + videoUrl);
//
//                        executeDownloadSegments(substringVideoIdFromLink(videoUrl), false);
//                    }
//
//                } catch (Exception e) {
//                    Log.e(TAG, "Cannot get link from object", e);
//                }
//            }
//        }).start();
//
//        Activity activity = playerActivity.get();
//        if (activity != null)
//            SponsorBlockUtils.addImageButton(activity);
    }

    /**
     * Called when it's time to update the UI with new second, about once per second, only when playing, also in background
     */
    public static void setCurrentVideoTime(long millis) {
        if (VERBOSE)
            Log.v(TAG, "setCurrentVideoTime: current video time: " + millis);
        if (!SponsorBlockSettings.isSponsorBlockEnabled) return;
        lastKnownVideoTime = millis;
        if (millis <= 0) return;
        //findAndSkipSegment(false);

        SponsorSegment[] segments = sponsorSegmentsOfCurrentVideo;
        if (segments == null || segments.length == 0) return;

        final long START_TIMER_BEFORE_SEGMENT_MILLIS = 1200;
        final long startTimerAtMillis = millis + START_TIMER_BEFORE_SEGMENT_MILLIS;

        for (final SponsorSegment segment : segments) {
            if (segment.start > millis) {
                if (segment.start > startTimerAtMillis)
                    break; // it's more then START_TIMER_BEFORE_SEGMENT_MILLIS far away
                if (!segment.category.behaviour.skip)
                    break;

                if (skipSponsorTask == null) {
                    if (VERBOSE)
                        Log.d(TAG, "Scheduling skipSponsorTask");
                    skipSponsorTask = new TimerTask() {
                        @Override
                        public void run() {
                            skipSponsorTask = null;
                            lastKnownVideoTime = segment.start + 1;
                            new Handler(Looper.getMainLooper()).post(findAndSkipSegmentRunnable);
                        }
                    };
                    sponsorTimer.schedule(skipSponsorTask, segment.start - millis);
                } else {
                    if (VERBOSE)
                        Log.d(TAG, "skipSponsorTask is already scheduled...");
                }

                break;
            }

            if (segment.end < millis)
                continue;

            // we are in the segment!
            if (segment.category.behaviour.skip) {
                sendViewRequestAsync(millis, segment);
                skipSegment(segment, false);
                break;
            } else {
                SkipSegmentView.show();
                return;
            }
        }
        SkipSegmentView.hide();
    }

    private static void sendViewRequestAsync(final long millis, final SponsorSegment segment) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (SponsorBlockSettings.countSkips &&
                        segment.category != SponsorBlockSettings.SegmentInfo.Preview &&
                        millis - segment.start < 2000) {
                    // Only skips from the start should count as a view
                    SponsorBlockUtils.sendViewCountRequest(segment);
                }
            }
        }).start();
    }

    /**
     * Called very high frequency (once every about 100ms), also in background. It sometimes triggers when a video is paused (couple times in the row with the same value)
     */
    public static void setCurrentVideoTimeHighPrecision(final long millis) {
        if (lastKnownVideoTime > 0)
            lastKnownVideoTime = millis;
        else
            setCurrentVideoTime(millis);
    }

    public static long getLastKnownVideoTime() {
        return lastKnownVideoTime;
    }

    /**
     * Called before onDraw method on time bar object, sets video length in millis
     */
    public static void setVideoLength(final long length) {
        if (VERBOSE_DRAW_OPTIONS)
            Log.d(TAG, "setVideoLength: length=" + length);
        currentVideoLength = length;
    }


    public static void setSponsorBarAbsoluteLeft(final Rect rect) {
        setSponsorBarAbsoluteLeft(rect.left);
    }

    public static void setSponsorBarAbsoluteLeft(final float left) {
        if (VERBOSE_DRAW_OPTIONS)
            Log.d(TAG, String.format("setSponsorBarLeft: left=%.2f", left));

        sponsorBarLeft = left;
    }

    public static void setSponsorBarRect(final Object self) {
        try {
            Rect rect = ((Rect) self.getClass().getField("e").get(self));
            if (rect != null) {
                setSponsorBarAbsoluteLeft(rect.left);
                setSponsorBarAbsoluteRight(rect.right);
            }
        } catch (IllegalAccessException | NoSuchFieldException e) {
            e.printStackTrace();
        }
    }

    public static void setSponsorBarAbsoluteRight(final Rect rect) {
        setSponsorBarAbsoluteRight(rect.right);
    }

    public static void setSponsorBarAbsoluteRight(final float right) {
        if (VERBOSE_DRAW_OPTIONS)
            Log.d(TAG, String.format("setSponsorBarRight: right=%.2f", right));

        sponsorBarRight = right;
    }

    public static void setSponsorBarThickness(final int thickness) {
        setSponsorBarThickness((float) thickness);
    }

    public static void setSponsorBarThickness(final float thickness) {
//        if (VERBOSE_DRAW_OPTIONS)
//            Log.d(TAG, String.format("setSponsorBarThickness: thickness=%.2f", thickness));

        sponsorBarThickness = thickness;
    }

    public static void onSkipSponsorClicked() {
        if (VERBOSE)
            Log.d(TAG, "Skip segment clicked");
        findAndSkipSegment(true);
    }


    public static void addSkipSponsorView15(final View view) {
        playerActivity = new WeakReference<>((Activity) view.getContext());
        if (VERBOSE)
            Log.d(TAG, "addSkipSponsorView15: view=" + view.toString());
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                final ViewGroup viewGroup = (ViewGroup) ((ViewGroup) view).getChildAt(2);
                Activity context = ((Activity) viewGroup.getContext());
                viewGroup.addView(new SkipSegmentView(context));
                viewGroup.addView(new NewSegmentHelperLayout(context));
                SponsorBlockUtils.addImageButton(context, 40);
            }
        }, 500);
    }

    public static void addSkipSponsorView14(final View view) {
        playerActivity = new WeakReference<>((Activity) view.getContext());
        if (VERBOSE)
            Log.d(TAG, "addSkipSponsorView14: view=" + view.toString());
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                final ViewGroup viewGroup = (ViewGroup) view.getParent();
                Activity activity = (Activity) viewGroup.getContext();
                viewGroup.addView(new SkipSegmentView(activity));
                viewGroup.addView(new NewSegmentHelperLayout(activity));

                // add image button when creating new activity
                SponsorBlockUtils.addImageButton(activity, 5);

//                InjectedPlugin.printViewStack(viewGroup, 0);

//                SponsorBlockUtils.addImageButton(activity);
            }
        }, 500);
    }


    /**
     * Called when it's time to draw time bar
     */
    public static void drawSponsorTimeBars(final Canvas canvas, final float posY) {
        if (sponsorBarThickness < 0.1) return;
        if (sponsorSegmentsOfCurrentVideo == null) return;


        final float thicknessDiv2 = sponsorBarThickness / 2;
        final float top = posY - thicknessDiv2;
        final float bottom = posY + thicknessDiv2;
        final float absoluteLeft = sponsorBarLeft;
        final float absoluteRight = sponsorBarRight;

        final float tmp1 = 1f / (float) currentVideoLength * (absoluteRight - absoluteLeft);
        for (SponsorSegment segment : sponsorSegmentsOfCurrentVideo) {
            float left = segment.start * tmp1 + absoluteLeft;
            float right = segment.end * tmp1 + absoluteLeft;
            canvas.drawRect(left, top, right, bottom, segment.category.paint);
        }
    }

    //    private final static Pattern videoIdRegex = Pattern.compile(".*\\.be\\/([A-Za-z0-9_\\-]{0,50}).*");
    public static String substringVideoIdFromLink(String link) {
        return link.substring(link.lastIndexOf('/') + 1);
    }

    public static void skipRelativeMilliseconds(int millisRelative) {
        skipToMillisecond(lastKnownVideoTime + millisRelative);
    }

    public static void skipToMillisecond(long millisecond) {
        // in 15.x if sponsor clip hits the end, then it crashes the app, because of too many function invocations
        // I put this block so that skip can be made only once per some time
        long now = System.currentTimeMillis();
        if (now < allowNextSkipRequestTime) {
            if (VERBOSE)
                Log.w(TAG, "skipToMillisecond: to fast, slow down, because you'll fail");
            return;
        }
        allowNextSkipRequestTime = now + 100;

        if (setMillisecondMethod == null) {
            Log.e(TAG, "setMillisecondMethod is null");
            return;
        }


        final Object currentObj = currentPlayerController.get();
        if (currentObj == null) {
            Log.e(TAG, "currentObj is null (might have been collected by GC)");
            return;
        }


        if (VERBOSE)
            Log.d(TAG, String.format("Requesting skip to millis=%d on thread %s", millisecond, Thread.currentThread().toString()));

        final long finalMillisecond = millisecond;
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    if (VERBOSE)
                        Log.i(TAG, "Skipping to millis=" + finalMillisecond);
                    lastKnownVideoTime = finalMillisecond;
                    setMillisecondMethod.invoke(currentObj, finalMillisecond);
                } catch (Exception e) {
                    Log.e(TAG, "Cannot skip to millisecond", e);
                }
            }
        });
    }


    private static void findAndSkipSegment(boolean wasClicked) {
        if (sponsorSegmentsOfCurrentVideo == null)
            return;

        final long millis = lastKnownVideoTime;

        for (SponsorSegment segment : sponsorSegmentsOfCurrentVideo) {
            if (segment.start > millis)
                break;

            if (segment.end < millis)
                continue;

            SkipSegmentView.show();
            if (!(segment.category.behaviour.skip || wasClicked))
                return;

            sendViewRequestAsync(millis, segment);
            skipSegment(segment, wasClicked);
            break;
        }

        SkipSegmentView.hide();
    }

    private static void skipSegment(SponsorSegment segment, boolean wasClicked) {
//        if (lastSkippedSegment == segment) return;
//        lastSkippedSegment = segment;
        if (VERBOSE)
            Log.d(TAG, "Skipping segment: " + segment.toString());

        if (SponsorBlockSettings.showToastWhenSkippedAutomatically && !wasClicked)
            SkipSegmentView.notifySkipped(segment);

        skipToMillisecond(segment.end + 2);
        SkipSegmentView.hide();
        if (segment.category == SponsorBlockSettings.SegmentInfo.Preview) {
            SponsorSegment[] newSegments = new SponsorSegment[sponsorSegmentsOfCurrentVideo.length - 1];
            int i = 0;
            for (SponsorSegment sponsorSegment : sponsorSegmentsOfCurrentVideo) {
                if (sponsorSegment != segment)
                    newSegments[i++] = sponsorSegment;
            }
            sponsorSegmentsOfCurrentVideo = newSegments;
        }
    }
}
