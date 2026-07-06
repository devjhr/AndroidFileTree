package ir.hanzodev1375.filetreeapp;

import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import android.widget.FrameLayout;
import ir.hanzodev1375.filetreelib.widget.FileTreeView;

public class CustomDrawerLayout extends DrawerLayout {
    private float initialX;
    private float initialY;
    private boolean isDragging;
    private VelocityTracker velocityTracker;
    private int touchSlop;
    private float lastX;
    private float lastY;
    private boolean isDrawerOpen;
    private ValueAnimator closeAnimator;
    private boolean shouldCloseDrawer = false;
    private float totalHorizontalScroll = 0;

    public CustomDrawerLayout(Context context) {
        super(context);
        init(context);
    }

    public CustomDrawerLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public CustomDrawerLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    private void init(Context context) {
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        velocityTracker = VelocityTracker.obtain();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        isDrawerOpen = isDrawerOpen(GravityCompat.START);

        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                initialX = ev.getX();
                initialY = ev.getY();
                lastX = initialX;
                lastY = initialY;
                totalHorizontalScroll = 0;
                shouldCloseDrawer = false;
                isDragging = false;
                if (velocityTracker != null) {
                    velocityTracker.clear();
                    velocityTracker.addMovement(ev);
                }

                if (closeAnimator != null && closeAnimator.isRunning()) {
                    closeAnimator.cancel();
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (velocityTracker != null) {
                    velocityTracker.addMovement(ev);
                }
                float dx = Math.abs(ev.getX() - initialX);
                float dy = Math.abs(ev.getY() - initialY);
                float currentDx = ev.getX() - lastX;

                if (isDrawerOpen && dx > dy && dx > touchSlop) {
                    if (isInDrawerContentArea(ev.getX(), ev.getY())) {
                        totalHorizontalScroll += currentDx;

                        if (!hasScrollableContent() && totalHorizontalScroll < -touchSlop) {
                            shouldCloseDrawer = true;
                            closeDrawerWithAnimation();
                            return false;
                        }

                        return false;
                    }
                }

                if (dx > touchSlop || dy > touchSlop) {
                    isDragging = true;
                }

                lastX = ev.getX();
                lastY = ev.getY();
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (velocityTracker != null) {
                    velocityTracker.clear();
                }
                isDragging = false;

                if (shouldCloseDrawer) {
                    closeDrawerWithAnimation();
                }
                break;
        }

        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        isDrawerOpen = isDrawerOpen(GravityCompat.START);

        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                initialX = ev.getX();
                initialY = ev.getY();
                lastX = initialX;
                lastY = initialY;
                totalHorizontalScroll = 0;
                shouldCloseDrawer = false;
                if (velocityTracker != null) {
                    velocityTracker.clear();
                    velocityTracker.addMovement(ev);
                }

                if (closeAnimator != null && closeAnimator.isRunning()) {
                    closeAnimator.cancel();
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (velocityTracker != null) {
                    velocityTracker.addMovement(ev);
                }
                float dx = Math.abs(ev.getX() - lastX);
                float dy = Math.abs(ev.getY() - lastY);
                float currentDx = ev.getX() - lastX;

                if (isDrawerOpen && dx > dy && dx > touchSlop) {
                    if (isInDrawerContentArea(ev.getX(), ev.getY())) {
                        totalHorizontalScroll += currentDx;

                        if (!hasScrollableContent() && totalHorizontalScroll < -touchSlop) {
                            shouldCloseDrawer = true;
                            closeDrawerWithAnimation();
                            return true;
                        }
                        return true;
                    }
                }

                lastX = ev.getX();
                lastY = ev.getY();
                break;

            case MotionEvent.ACTION_UP:
                if (isDrawerOpen && isInDrawerContentArea(ev.getX(), ev.getY())) {
                    if (!hasScrollableContent() && shouldCloseDrawer) {
                        closeDrawerWithAnimation();
                        return true;
                    }
                }

                if (velocityTracker != null) {
                    velocityTracker.clear();
                }
                break;

            case MotionEvent.ACTION_CANCEL:
                if (velocityTracker != null) {
                    velocityTracker.clear();
                }
                break;
        }

        return super.onTouchEvent(ev);
    }

    private void closeDrawerWithAnimation() {
        if (closeAnimator != null && closeAnimator.isRunning()) {
            closeAnimator.cancel();
        }

        closeAnimator = ValueAnimator.ofFloat(1f, 0f);
        closeAnimator.setDuration(200);
        closeAnimator.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();
            if (progress == 0f) {
                closeDrawer(GravityCompat.START);
            }
        });
        closeAnimator.start();
    }

    private boolean isInDrawerContentArea(float x, float y) {
        int drawerWidth = getWidth() / 2;
        return x < drawerWidth;
    }

    private boolean hasScrollableContent() {
        if (getChildCount() > 0) {
            for (int i = 0; i < getChildCount(); i++) {
                android.view.View child = getChildAt(i);
                if (child instanceof FrameLayout && 
                    ((FrameLayout) child).getLayoutParams() instanceof DrawerLayout.LayoutParams) {
                    DrawerLayout.LayoutParams params = (DrawerLayout.LayoutParams) child.getLayoutParams();
                    if (params.gravity == GravityCompat.START) {
                        if (child instanceof FrameLayout) {
                            FrameLayout frame = (FrameLayout) child;
                            if (frame.getChildCount() > 0) {
                                android.view.View content = frame.getChildAt(0);
                                if (content instanceof FileTreeView) {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
        if (closeAnimator != null) {
            closeAnimator.cancel();
            closeAnimator = null;
        }
    }
}