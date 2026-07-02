package ir.hanzodev1375.filetreelib.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.FocusFinder;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Scroller;
import android.widget.TextView;
import androidx.annotation.NonNull;
import ir.hanzodev1375.filetreelib.zoom.ZoomManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Layout container for a view hierarchy that can be scrolled by the user, allowing it to be larger
 * than the physical display. A TwoDScrollView is a {@link FrameLayout}, meaning you should place
 * one child in it containing the entire contents to scroll; this child may itself be a layout
 * manager with a complex hierarchy of objects. A child that is often used is a {@link LinearLayout}
 * in a vertical orientation, presenting a vertical array of top-level items that the user can
 * scroll through.
 *
 * <p>
 *
 * <p>The {@link TextView} class also takes care of its own scrolling, so does not require a
 * TwoDScrollView, but using the two together is possible to achieve the effect of a text view
 * within a larger container.
 */
public class TwoDScrollView extends FrameLayout {
  static final int ANIMATED_SCROLL_GAP = 250;
  static final float MAX_SCROLL_FACTOR = 0.5f;
  private final Rect mTempRect = new Rect();
  private long mLastScroll;
  private Scroller mScroller;

  /**
   * Flag to indicate that we are moving focus ourselves. This is so the code that watches for focus
   * changes initiated outside this TwoDScrollView knows that it does not have to do anything.
   */
  private boolean mTwoDScrollViewMovedFocus;

  /** Position of the last motion event. */
  private float mLastMotionY;

  private float mLastMotionX;

  /**
   * True when the layout has changed but the traversal has not come through yet. Ideally the view
   * hierarchy would keep track of this for us.
   */
  private boolean mIsLayoutDirty = true;

  /**
   * The child to give focus to in the event that a child has requested focus while the layout is
   * dirty. This prevents the scroll from being wrong if the child has not been laid out before
   * requesting focus.
   */
  private View mChildToScrollTo = null;

  /**
   * True if the user is currently dragging this TwoDScrollView around. This is not the same as 'is
   * being flinged', which can be checked by mScroller.isFinished() (flinging begins when the user
   * lifts his finger).
   */
  private boolean mIsBeingDragged = false;

  /** Determines speed during touch scrolling */
  private VelocityTracker mVelocityTracker;

  /** Whether arrow scrolling is animated. */
  private int mTouchSlop;

  private int mMinimumVelocity;
  private int mMaximumVelocity;

  /** Pinch-to-zoom state (bounds, current level, enabled/disabled) — see {@link ZoomManager}. */
  private final ZoomManager zoomManager = new ZoomManager();

  private ScaleGestureDetector scaleGestureDetector;

  /** True while a 2-finger pinch gesture is actively in progress. */
  private boolean mIsScaling = false;

  private final List<OnScrollListener> scrollListeners = new ArrayList<>();

  /** Callback invoked whenever this view's scroll position changes. */
  public interface OnScrollListener {
    void onScrollChanged(int left, int top, int oldLeft, int oldTop);
  }

  public void addOnScrollListener(OnScrollListener listener) {
    scrollListeners.add(listener);
  }

  public boolean removeOnScrollListener(OnScrollListener listener) {
    return scrollListeners.remove(listener);
  }

  @Override
  protected void onScrollChanged(int l, int t, int oldl, int oldt) {
    super.onScrollChanged(l, t, oldl, oldt);
    for (OnScrollListener listener : scrollListeners) {
      listener.onScrollChanged(l, t, oldl, oldt);
    }
  }

  public TwoDScrollView(Context context) {
    super(context);
    initTwoDScrollView();
  }

  private void initTwoDScrollView() {
    mScroller = new Scroller(getContext());
    setFocusable(true);
    setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
    setWillNotDraw(false);
    final ViewConfiguration configuration = ViewConfiguration.get(getContext());
    mTouchSlop = configuration.getScaledTouchSlop();
    mMinimumVelocity = configuration.getScaledMinimumFlingVelocity();
    mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();

    zoomManager.setOnZoomChangeListener(this::applyZoomChange);
    scaleGestureDetector =
        new ScaleGestureDetector(
            getContext(),
            new ScaleGestureDetector.SimpleOnScaleGestureListener() {
              @Override
              public boolean onScaleBegin(@NonNull ScaleGestureDetector detector) {
                if (!zoomManager.isZoomMod()) return false;
                mIsScaling = true;
                mIsBeingDragged = false;
                if (!mScroller.isFinished()) mScroller.abortAnimation();
                final ViewParent parent = getParent();
                if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
                return true;
              }

              @Override
              public boolean onScale(@NonNull ScaleGestureDetector detector) {
                float target = zoomManager.getCurrentZoomFactor() * detector.getScaleFactor();
                zoomManager.setCurrentZoomFactor(
                    target, detector.getFocusX(), detector.getFocusY());
                return true;
              }

              @Override
              public void onScaleEnd(@NonNull ScaleGestureDetector detector) {
                mIsScaling = false;
              }
            });
  }

  public TwoDScrollView(Context context, AttributeSet attrs) {
    super(context, attrs);
    initTwoDScrollView();
  }

  public TwoDScrollView(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    initTwoDScrollView();
  }

  /**
   * @return The maximum amount this scroll view will scroll in response to an arrow event.
   */
  public int getMaxScrollAmountVertical() {
    return (int) (MAX_SCROLL_FACTOR * getHeight());
  }

  public int getMaxScrollAmountHorizontal() {
    return (int) (MAX_SCROLL_FACTOR * getWidth());
  }

  /**
   * You can call this function yourself to have the scroll view perform scrolling from a key event,
   * just as if the event had been dispatched to it by the view hierarchy.
   *
   * @param event The key event to execute.
   * @return Return true if the event was handled, else false.
   */
  public boolean executeKeyEvent(KeyEvent event) {
    mTempRect.setEmpty();
    if (!canScroll()) {
      if (isFocused()) {
        View currentFocused = findFocus();
        if (currentFocused == this) currentFocused = null;
        View nextFocused =
            FocusFinder.getInstance().findNextFocus(this, currentFocused, View.FOCUS_DOWN);
        return nextFocused != null
            && nextFocused != this
            && nextFocused.requestFocus(View.FOCUS_DOWN);
      }
      return false;
    }
    boolean handled = false;
    if (event.getAction() == KeyEvent.ACTION_DOWN) {
      switch (event.getKeyCode()) {
        case KeyEvent.KEYCODE_DPAD_UP:
          if (!event.isAltPressed()) {
            handled = arrowScroll(View.FOCUS_UP, false);
          } else {
            handled = fullScroll(View.FOCUS_UP, false);
          }
          break;
        case KeyEvent.KEYCODE_DPAD_DOWN:
          if (!event.isAltPressed()) {
            handled = arrowScroll(View.FOCUS_DOWN, false);
          } else {
            handled = fullScroll(View.FOCUS_DOWN, false);
          }
          break;
        case KeyEvent.KEYCODE_DPAD_LEFT:
          if (!event.isAltPressed()) {
            handled = arrowScroll(View.FOCUS_LEFT, true);
          } else {
            handled = fullScroll(View.FOCUS_LEFT, true);
          }
          break;
        case KeyEvent.KEYCODE_DPAD_RIGHT:
          if (!event.isAltPressed()) {
            handled = arrowScroll(View.FOCUS_RIGHT, true);
          } else {
            handled = fullScroll(View.FOCUS_RIGHT, true);
          }
          break;
      }
    }
    return handled;
  }

  @SuppressLint("ClickableViewAccessibility")
  @Override
  public boolean onTouchEvent(MotionEvent ev) {

    if (zoomManager.isZoomMod()) {
      scaleGestureDetector.onTouchEvent(ev);

      if (mIsScaling || ev.getPointerCount() > 1) {
        // A pinch is in progress (or just ended this frame) — don't run the single-finger
        // scroll/fling logic below for this event.
        switch (ev.getActionMasked()) {
          case MotionEvent.ACTION_POINTER_UP:
            {
              // Re-anchor tracking to whichever finger remains so that the next single-finger
              // MOVE continues smoothly instead of jump-scrolling.
              int liftedIndex = ev.getActionIndex();
              int remainingIndex = liftedIndex == 0 ? 1 : 0;
              if (remainingIndex < ev.getPointerCount()) {
                mLastMotionX = ev.getX(remainingIndex);
                mLastMotionY = ev.getY(remainingIndex);
              }
              break;
            }
          case MotionEvent.ACTION_UP:
          case MotionEvent.ACTION_CANCEL:
            mIsBeingDragged = false;
            mIsScaling = false;
            break;
        }
        return true;
      }
    }

    if (ev.getAction() == MotionEvent.ACTION_DOWN && ev.getEdgeFlags() != 0) {
      // Don't handle edge touches immediately -- they may actually belong to one of our
      // descendants.
      return false;
    }

    if (!canScroll()) {
      return false;
    }

    if (mVelocityTracker == null) {
      mVelocityTracker = VelocityTracker.obtain();
    }
    mVelocityTracker.addMovement(ev);

    final int action = ev.getAction();
    final float y = ev.getY();
    final float x = ev.getX();

    switch (action) {
      case MotionEvent.ACTION_DOWN:
        /*
         * If being flinged and user touches, stop the fling. isFinished
         * will be false if being flinged.
         */
        if (!mScroller.isFinished()) {
          mScroller.abortAnimation();
        }

        // Remember where the motion event started
        mLastMotionY = y;
        mLastMotionX = x;
        break;
      case MotionEvent.ACTION_MOVE:
        // Scroll to follow the motion event
        int deltaX = (int) (mLastMotionX - x);
        int deltaY = (int) (mLastMotionY - y);
        mLastMotionX = x;
        mLastMotionY = y;

        if (deltaX < 0) {
          if (getScrollX() < 0) {
            deltaX = 0;
          }
        } else if (deltaX > 0) {
          final int rightEdge = getWidth() - getPaddingRight();
          // Use the zoom-scaled width, otherwise dragging can't reach the edges of zoomed-in
          // content.
          final int scaledRight = getChildAt(0).getLeft() + scaledChildWidth(getChildAt(0));
          final int availableToScroll = scaledRight - getScrollX() - rightEdge;
          if (availableToScroll > 0) {
            deltaX = Math.min(availableToScroll, deltaX);
          } else {
            deltaX = 0;
          }
        }
        if (deltaY < 0) {
          if (getScrollY() < 0) {
            deltaY = 0;
          }
        } else if (deltaY > 0) {
          final int bottomEdge = getHeight() - getPaddingBottom();
          final int scaledBottom = getChildAt(0).getTop() + scaledChildHeight(getChildAt(0));
          final int availableToScroll = scaledBottom - getScrollY() - bottomEdge;
          if (availableToScroll > 0) {
            deltaY = Math.min(availableToScroll, deltaY);
          } else {
            deltaY = 0;
          }
        }
        if (deltaY != 0 || deltaX != 0) scrollBy(deltaX, deltaY);
        break;
      case MotionEvent.ACTION_UP:
        final VelocityTracker velocityTracker = mVelocityTracker;
        velocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
        int initialXVelocity = (int) velocityTracker.getXVelocity();
        int initialYVelocity = (int) velocityTracker.getYVelocity();
        if ((Math.abs(initialXVelocity) + Math.abs(initialYVelocity) > mMinimumVelocity)
            && getChildCount() > 0) {
          fling(-initialXVelocity, -initialYVelocity);
        }
        if (mVelocityTracker != null) {
          mVelocityTracker.recycle();
          mVelocityTracker = null;
        }
        break;
      case MotionEvent.ACTION_CANCEL:
        mIsBeingDragged = false;
        if (mVelocityTracker != null) {
          mVelocityTracker.recycle();
          mVelocityTracker = null;
        }
    }
    return true;
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);

    View currentFocused = findFocus();
    if (null == currentFocused || this == currentFocused) return;

    // If the currently-focused view was visible on the screen when the
    // screen was at the old height, then scroll the screen to make that
    // view visible with the new screen height.
    currentFocused.getDrawingRect(mTempRect);
    offsetDescendantRectToMyCoords(currentFocused, mTempRect);
    int scrollDeltaX = computeScrollDeltaToGetChildRectOnScreen(mTempRect);
    int scrollDeltaY = computeScrollDeltaToGetChildRectOnScreen(mTempRect);
    doScroll(scrollDeltaX, scrollDeltaY);
  }

  /**
   * Smooth scroll by a Y delta
   *
   * @param delta the number of pixels to scroll by on the Y axis
   */
  private void doScroll(int deltaX, int deltaY) {
    if (deltaX != 0 || deltaY != 0) {
      smoothScrollBy(deltaX, deltaY);
    }
  }

  /** The child's laid-out width, scaled by the current zoom factor. */
  private int scaledChildWidth(@NonNull View child) {
    return Math.round(child.getWidth() * zoomManager.getCurrentZoomFactor());
  }

  /** The child's laid-out height, scaled by the current zoom factor. */
  private int scaledChildHeight(@NonNull View child) {
    return Math.round(child.getHeight() * zoomManager.getCurrentZoomFactor());
  }

  /**
   * {@link ZoomManager.OnZoomChangeListener} callback — actually applies the new zoom factor to our
   * child (a scale transform, exactly like zooming into a photo) and adjusts the scroll offset so
   * the point under the pinch focus (or the viewport center, for programmatic zoom calls) stays
   * visually fixed.
   */
  private void applyZoomChange(float oldFactor, float newFactor, float focusX, float focusY) {
    if (getChildCount() == 0) return;
    View child = getChildAt(0);

    if (focusX < 0 || focusY < 0) {
      // No explicit pinch focus point (e.g. a programmatic setCurrentZoomScale call) —
      // zoom around the center of the current viewport instead.
      focusX = (getWidth() - getPaddingLeft() - getPaddingRight()) / 2f;
      focusY = (getHeight() - getPaddingTop() - getPaddingBottom()) / 2f;
    }

    float viewportX = focusX - getPaddingLeft();
    float viewportY = focusY - getPaddingTop();

    // The content-space point currently sitting under the focus point, before re-scaling.
    float contentX = (getScrollX() + viewportX) / oldFactor;
    float contentY = (getScrollY() + viewportY) / oldFactor;

    child.setPivotX(0f);
    child.setPivotY(0f);
    child.setScaleX(newFactor);
    child.setScaleY(newFactor);

    // Re-anchor scroll so that same content point is still under the focus point.
    scrollTo(
        Math.round(contentX * newFactor - viewportX), Math.round(contentY * newFactor - viewportY));
    invalidate();
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   *
   * <p>This version also clamps the scrolling to the bounds of our child.
   */
  public void scrollTo(int x, int y) {
    // we rely on the fact the View.scrollBy calls scrollTo.
    if (getChildCount() > 0) {
      View child = getChildAt(0);
      x = clamp(x, getWidth() - getPaddingRight() - getPaddingLeft(), scaledChildWidth(child));
      y = clamp(y, getHeight() - getPaddingBottom() - getPaddingTop(), scaledChildHeight(child));
      if (x != getScrollX() || y != getScrollY()) {
        super.scrollTo(x, y);
      }
    }
  }

  @Override
  public void computeScroll() {
    if (mScroller.computeScrollOffset()) {
      // This is called at drawing time by ViewGroup.  We don't want to
      // re-show the scrollbars at this point, which scrollTo will do,
      // so we replicate most of scrollTo here.
      //
      //         It's a little odd to call onScrollChanged from inside the drawing.
      //
      //         It is, except when you remember that computeScroll() is used to
      //         animate scrolling. So unless we want to defer the onScrollChanged()
      //         until the end of the animated scrolling, we don't really have a
      //         choice here.
      //
      //         I agree.  The alternative, which I think would be worse, is to post
      //         something and tell the subclasses later.  This is bad because there
      //         will be a window where mScrollX/Y is different from what the app
      //         thinks it is.
      //
      int oldX = getScrollX();
      int oldY = getScrollY();
      int x = mScroller.getCurrX();
      int y = mScroller.getCurrY();
      if (getChildCount() > 0) {
        View child = getChildAt(0);
        scrollTo(
            clamp(x, getWidth() - getPaddingRight() - getPaddingLeft(), scaledChildWidth(child)),
            clamp(y, getHeight() - getPaddingBottom() - getPaddingTop(), scaledChildHeight(child)));
      } else {
        scrollTo(x, y);
      }
      if (oldX != getScrollX() || oldY != getScrollY()) {
        onScrollChanged(getScrollX(), getScrollY(), oldX, oldY);
      }

      // Keep on drawing until the animation has finished.
      postInvalidate();
    }
  }

  @Override
  protected float getTopFadingEdgeStrength() {
    if (getChildCount() == 0) {
      return 0.0f;
    }
    final int length = getVerticalFadingEdgeLength();
    if (getScrollY() < length) {
      return getScrollY() / (float) length;
    }
    return 1.0f;
  }

  @Override
  protected float getBottomFadingEdgeStrength() {
    if (getChildCount() == 0) {
      return 0.0f;
    }
    final int length = getVerticalFadingEdgeLength();
    final int bottomEdge = getHeight() - getPaddingBottom();
    final int span = getChildAt(0).getBottom() - getScrollY() - bottomEdge;
    if (span < length) {
      return span / (float) length;
    }
    return 1.0f;
  }

  @Override
  protected float getLeftFadingEdgeStrength() {
    if (getChildCount() == 0) {
      return 0.0f;
    }
    final int length = getHorizontalFadingEdgeLength();
    if (getScrollX() < length) {
      return getScrollX() / (float) length;
    }
    return 1.0f;
  }

  @Override
  protected float getRightFadingEdgeStrength() {
    if (getChildCount() == 0) {
      return 0.0f;
    }
    final int length = getHorizontalFadingEdgeLength();
    final int rightEdge = getWidth() - getPaddingRight();
    final int span = getChildAt(0).getRight() - getScrollX() - rightEdge;
    if (span < length) {
      return span / (float) length;
    }
    return 1.0f;
  }

  @Override
  protected int computeHorizontalScrollRange() {
    int count = getChildCount();
    return count == 0 ? getWidth() : scaledChildWidth(getChildAt(0));
  }

  /** The scroll range of a scroll view is the overall height of all of its children. */
  @Override
  protected int computeVerticalScrollRange() {
    int count = getChildCount();
    return count == 0 ? getHeight() : scaledChildHeight(getChildAt(0));
  }

  @Override
  public void requestLayout() {
    mIsLayoutDirty = true;
    super.requestLayout();
  }

  private int clamp(int n, int my, int child) {
    if (my >= child || n < 0) {
      /* my >= child is this case:
       *                    |--------------- me ---------------|
       *     |------ child ------|
       * or
       *     |--------------- me ---------------|
       *            |------ child ------|
       * or
       *     |--------------- me ---------------|
       *                                  |------ child ------|
       *
       * n < 0 is this case:
       *     |------ me ------|
       *                    |-------- child --------|
       *     |-- mScrollX --|
       */
      return 0;
    }
    if ((my + n) > child) {
      /* this case:
       *                    |------ me ------|
       *     |------ child ------|
       *     |-- mScrollX --|
       */
      return child - my;
    }
    return n;
  }

  /**
   * Fling the scroll view
   *
   * @param velocityY The initial velocity in the Y direction. Positive numbers mean that the
   *     finger/curor is moving down the screen, which means we want to scroll towards the top.
   */
  public void fling(int velocityX, int velocityY) {
    if (getChildCount() > 0) {
      int height = getHeight() - getPaddingBottom() - getPaddingTop();
      int bottom = scaledChildHeight(getChildAt(0));
      int width = getWidth() - getPaddingRight() - getPaddingLeft();
      int right = scaledChildWidth(getChildAt(0));

      mScroller.fling(
          getScrollX(), getScrollY(), velocityX, velocityY, 0, right - width, 0, bottom - height);

      final boolean movingDown = velocityY > 0;
      final boolean movingRight = velocityX > 0;

      View newFocused =
          findFocusableViewInMyBounds(
              movingRight, mScroller.getFinalX(), movingDown, mScroller.getFinalY(), findFocus());
      if (newFocused == null) {
        newFocused = this;
      }

      if (newFocused != findFocus()
          && newFocused.requestFocus(movingDown ? View.FOCUS_DOWN : View.FOCUS_UP)) {
        mTwoDScrollViewMovedFocus = true;
        mTwoDScrollViewMovedFocus = false;
      }

      awakenScrollBars(mScroller.getDuration());
      invalidate();
    }
  }

  /**
   * Finds the next focusable component that fits in this View's bounds (excluding fading edges)
   * pretending that this View's top is located at the parameter top.
   *
   * @param topFocus look for a candidate is the one at the top of the bounds if topFocus is true,
   *     or at the bottom of the bounds if topFocus is false
   * @param top the top offset of the bounds in which a focusable must be found (the fading edge is
   *     assumed to start at this position)
   * @param preferredFocusable the View that has highest priority and will be returned if it is
   *     within my bounds (null is valid)
   * @return the next focusable component in the bounds or null if none can be found
   */
  private View findFocusableViewInMyBounds(
      final boolean topFocus,
      final int top,
      final boolean leftFocus,
      final int left,
      View preferredFocusable) {
    /*
     * The fading edge's transparent side should be considered for focus
     * since it's mostly visible, so we divide the actual fading edge length
     * by 2.
     */
    final int verticalFadingEdgeLength = getVerticalFadingEdgeLength() / 2;
    final int topWithoutFadingEdge = top + verticalFadingEdgeLength;
    final int bottomWithoutFadingEdge = top + getHeight() - verticalFadingEdgeLength;
    final int horizontalFadingEdgeLength = getHorizontalFadingEdgeLength() / 2;
    final int leftWithoutFadingEdge = left + horizontalFadingEdgeLength;
    final int rightWithoutFadingEdge = left + getWidth() - horizontalFadingEdgeLength;

    if ((preferredFocusable != null)
        && (preferredFocusable.getTop() < bottomWithoutFadingEdge)
        && (preferredFocusable.getBottom() > topWithoutFadingEdge)
        && (preferredFocusable.getLeft() < rightWithoutFadingEdge)
        && (preferredFocusable.getRight() > leftWithoutFadingEdge)) {
      return preferredFocusable;
    }
    return findFocusableViewInBounds(
        topFocus,
        topWithoutFadingEdge,
        bottomWithoutFadingEdge,
        leftFocus,
        leftWithoutFadingEdge,
        rightWithoutFadingEdge);
  }

  /**
   * Finds the next focusable component that fits in the specified bounds.
   *
   * @param topFocus look for a candidate is the one at the top of the bounds if topFocus is true,
   *     or at the bottom of the bounds if topFocus is false
   * @param top the top offset of the bounds in which a focusable must be found
   * @param bottom the bottom offset of the bounds in which a focusable must be found
   * @return the next focusable component in the bounds or null if none can be found
   */
  private View findFocusableViewInBounds(
      boolean topFocus, int top, int bottom, boolean leftFocus, int left, int right) {
    List<View> focusables = getFocusables(View.FOCUS_FORWARD);
    View focusCandidate = null;

    /*
     * A fully contained focusable is one where its top is below the bound's
     * top, and its bottom is above the bound's bottom. A partially
     * contained focusable is one where some part of it is within the
     * bounds, but it also has some part that is not within bounds.  A fully contained
     * focusable is preferred to a partially contained focusable.
     */
    boolean foundFullyContainedFocusable = false;

    int count = focusables.size();
    for (int i = 0; i < count; i++) {
      View view = focusables.get(i);
      int viewTop = view.getTop();
      int viewBottom = view.getBottom();
      int viewLeft = view.getLeft();
      int viewRight = view.getRight();

      if (top < viewBottom && viewTop < bottom && left < viewRight && viewLeft < right) {
        /*
         * the focusable is in the target area, it is a candidate for
         * focusing
         */
        final boolean viewIsFullyContained =
            (top < viewTop) && (viewBottom < bottom) && (left < viewLeft) && (viewRight < right);
        if (focusCandidate == null) {
          /* No candidate, take this one */
          focusCandidate = view;
          foundFullyContainedFocusable = viewIsFullyContained;
        } else {
          final boolean viewIsCloserToVerticalBoundary =
              (topFocus && viewTop < focusCandidate.getTop())
                  || (!topFocus && viewBottom > focusCandidate.getBottom());
          final boolean viewIsCloserToHorizontalBoundary =
              (leftFocus && viewLeft < focusCandidate.getLeft())
                  || (!leftFocus && viewRight > focusCandidate.getRight());
          if (foundFullyContainedFocusable) {
            if (viewIsFullyContained
                && viewIsCloserToVerticalBoundary
                && viewIsCloserToHorizontalBoundary) {
              /*
               * We're dealing with only fully contained views, so
               * it has to be closer to the boundary to beat our
               * candidate
               */
              focusCandidate = view;
            }
          } else {
            if (viewIsFullyContained) {
              /* Any fully contained view beats a partially contained view */
              focusCandidate = view;
              foundFullyContainedFocusable = true;
            } else if (viewIsCloserToVerticalBoundary && viewIsCloserToHorizontalBoundary) {
              /*
               * Partially contained view beats another partially
               * contained view if it's closer
               */
              focusCandidate = view;
            }
          }
        }
      }
    }
    return focusCandidate;
  }

  /**
   * Handles scrolling in response to a "home/end" shortcut press. This method will scroll the view
   * to the top or bottom and give the focus to the topmost/bottommost component in the new visible
   * area. If no component is a good candidate for focus, this scrollview reclaims the focus.
   *
   * @param direction the scroll direction: {@link android.view.View#FOCUS_UP} to go the top of the
   *     view or {@link android.view.View#FOCUS_DOWN} to go the bottom
   * @return true if the key event is consumed by this method, false otherwise
   */
  public boolean fullScroll(int direction, boolean horizontal) {
    if (!horizontal) {
      boolean down = direction == View.FOCUS_DOWN;
      int height = getHeight();
      mTempRect.top = 0;
      mTempRect.bottom = height;
      if (down) {
        int count = getChildCount();
        if (count > 0) {
          View view = getChildAt(count - 1);
          mTempRect.bottom = view.getBottom();
          mTempRect.top = mTempRect.bottom - height;
        }
      }
      return scrollAndFocus(direction, mTempRect.top, mTempRect.bottom, 0, 0, 0);
    } else {
      boolean right = direction == View.FOCUS_DOWN;
      int width = getWidth();
      mTempRect.left = 0;
      mTempRect.right = width;
      if (right) {
        int count = getChildCount();
        if (count > 0) {
          View view = getChildAt(count - 1);
          mTempRect.right = view.getBottom();
          mTempRect.left = mTempRect.right - width;
        }
      }
      return scrollAndFocus(0, 0, 0, direction, mTempRect.top, mTempRect.bottom);
    }
  }

  /**
   * Handle scrolling in response to an up or down arrow click.
   *
   * @param direction The direction corresponding to the arrow key that was pressed
   * @return True if we consumed the event, false otherwise
   */
  public boolean arrowScroll(int direction, boolean horizontal) {
    View currentFocused = findFocus();
    if (currentFocused == this) currentFocused = null;
    View nextFocused = FocusFinder.getInstance().findNextFocus(this, currentFocused, direction);
    final int maxJump = horizontal ? getMaxScrollAmountHorizontal() : getMaxScrollAmountVertical();

    if (!horizontal) {
      if (nextFocused != null) {
        nextFocused.getDrawingRect(mTempRect);
        offsetDescendantRectToMyCoords(nextFocused, mTempRect);
        int scrollDelta = computeScrollDeltaToGetChildRectOnScreen(mTempRect);
        doScroll(0, scrollDelta);
        nextFocused.requestFocus(direction);
      } else {
        // no new focus
        int scrollDelta = maxJump;
        if (direction == View.FOCUS_UP && getScrollY() < scrollDelta) {
          scrollDelta = getScrollY();
        } else if (direction == View.FOCUS_DOWN) {
          if (getChildCount() > 0) {
            int daBottom = getChildAt(0).getBottom();
            int screenBottom = getScrollY() + getHeight();
            if (daBottom - screenBottom < maxJump) {
              scrollDelta = daBottom - screenBottom;
            }
          }
        }
        if (scrollDelta == 0) {
          return false;
        }
        doScroll(0, direction == View.FOCUS_DOWN ? scrollDelta : -scrollDelta);
      }
    } else {
      if (nextFocused != null) {
        nextFocused.getDrawingRect(mTempRect);
        offsetDescendantRectToMyCoords(nextFocused, mTempRect);
        int scrollDelta = computeScrollDeltaToGetChildRectOnScreen(mTempRect);
        doScroll(scrollDelta, 0);
        nextFocused.requestFocus(direction);
      } else {
        // no new focus
        int scrollDelta = maxJump;
        if (direction == View.FOCUS_UP && getScrollY() < scrollDelta) {
          scrollDelta = getScrollY();
        } else if (direction == View.FOCUS_DOWN) {
          if (getChildCount() > 0) {
            int daBottom = getChildAt(0).getBottom();
            int screenBottom = getScrollY() + getHeight();
            if (daBottom - screenBottom < maxJump) {
              scrollDelta = daBottom - screenBottom;
            }
          }
        }
        if (scrollDelta == 0) {
          return false;
        }
        doScroll(direction == View.FOCUS_DOWN ? scrollDelta : -scrollDelta, 0);
      }
    }
    return true;
  }

  /**
   * Like {@link #scrollTo}, but scroll smoothly instead of immediately.
   *
   * @param x the position where to scroll on the X axis
   * @param y the position where to scroll on the Y axis
   */
  public final void smoothScrollTo(int x, int y) {
    smoothScrollBy(x - getScrollX(), y - getScrollY());
  }

  /**
   * Like {@link View#scrollBy}, but scroll smoothly instead of immediately.
   *
   * @param dx the number of pixels to scroll by on the X axis
   * @param dy the number of pixels to scroll by on the Y axis
   */
  public final void smoothScrollBy(int dx, int dy) {
    long duration = AnimationUtils.currentAnimationTimeMillis() - mLastScroll;
    if (duration > ANIMATED_SCROLL_GAP) {
      mScroller.startScroll(getScrollX(), getScrollY(), dx, dy);
      awakenScrollBars(mScroller.getDuration());
      invalidate();
    } else {
      if (!mScroller.isFinished()) {
        mScroller.abortAnimation();
      }
      scrollBy(dx, dy);
    }
    mLastScroll = AnimationUtils.currentAnimationTimeMillis();
  }

  @Override
  public void requestChildFocus(View child, View focused) {
    if (!mTwoDScrollViewMovedFocus) {
      if (!mIsLayoutDirty) {
        scrollToChild(focused);
      } else {
        // The child may not be laid out yet, we can't compute the scroll yet
        mChildToScrollTo = focused;
      }
    }
    super.requestChildFocus(child, focused);
  }

  /**
   * Scrolls the view to the given child.
   *
   * @param child the View to scroll to
   */
  private void scrollToChild(View child) {
    child.getDrawingRect(mTempRect);
    /* Offset from child's local coordinates to TwoDScrollView coordinates */
    offsetDescendantRectToMyCoords(child, mTempRect);
    int scrollDelta = computeScrollDeltaToGetChildRectOnScreen(mTempRect);
    if (scrollDelta != 0) {
      scrollBy(0, scrollDelta);
    }
  }

  /**
   * Compute the amount to scroll in the Y direction in order to get a rectangle completely on the
   * screen (or, if taller than the screen, at least the first screen size chunk of it).
   *
   * @param rect The rect.
   * @return The scroll delta.
   */
  protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) {
    if (getChildCount() == 0) return 0;
    int height = getHeight();
    int screenTop = getScrollY();
    int screenBottom = screenTop + height;
    int fadingEdge = getVerticalFadingEdgeLength();
    // leave room for top fading edge as long as rect isn't at very top
    if (rect.top > 0) {
      screenTop += fadingEdge;
    }

    // leave room for bottom fading edge as long as rect isn't at very bottom
    if (rect.bottom < getChildAt(0).getHeight()) {
      screenBottom -= fadingEdge;
    }
    int scrollYDelta = 0;
    if (rect.bottom > screenBottom && rect.top > screenTop) {
      // need to move down to get it in view: move down just enough so
      // that the entire rectangle is in view (or at least the first
      // screen size chunk).
      if (rect.height() > height) {
        // just enough to get screen size chunk on
        scrollYDelta += (rect.top - screenTop);
      } else {
        // get entire rect at bottom of screen
        scrollYDelta += (rect.bottom - screenBottom);
      }

      // make sure we aren't scrolling beyond the end of our content
      int bottom = getChildAt(0).getBottom();
      int distanceToBottom = bottom - screenBottom;
      scrollYDelta = Math.min(scrollYDelta, distanceToBottom);

    } else if (rect.top < screenTop && rect.bottom < screenBottom) {
      // need to move up to get it in view: move up just enough so that
      // entire rectangle is in view (or at least the first screen
      // size chunk of it).

      if (rect.height() > height) {
        // screen size chunk
        scrollYDelta -= (screenBottom - rect.bottom);
      } else {
        // entire rect at top
        scrollYDelta -= (screenTop - rect.top);
      }

      // make sure we aren't scrolling any further than the top our content
      scrollYDelta = Math.max(scrollYDelta, -getScrollY());
    }
    return scrollYDelta;
  }

  @Override
  public boolean requestChildRectangleOnScreen(View child, Rect rectangle, boolean immediate) {
    // offset into coordinate space of this scroll view
    rectangle.offset(child.getLeft() - child.getScrollX(), child.getTop() - child.getScrollY());
    return scrollToChildRect(rectangle, immediate);
  }

  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    // Let the focused view and/or our descendants get the key first
    boolean handled = super.dispatchKeyEvent(event);
    if (handled) {
      return true;
    }
    return executeKeyEvent(event);
  }

  @Override
  public boolean onInterceptTouchEvent(MotionEvent ev) {
    /*
     * This method JUST determines whether we want to intercept the motion.
     * If we return true, onMotionEvent will be called and we do the actual
     * scrolling there.
     *
     * Shortcut the most recurring case: the user is in the dragging
     * state and he is moving his finger.  We want to intercept this
     * motion.
     */
    if (zoomManager.isZoomMod() && (mIsScaling || ev.getPointerCount() > 1)) {
      // Claim the gesture for pinch-zooming as soon as a second finger touches down.
      // (The event itself is fed to scaleGestureDetector once, inside onTouchEvent.)
      final ViewParent zoomParent = getParent();
      if (zoomParent != null) zoomParent.requestDisallowInterceptTouchEvent(true);
      return true;
    }

    final int action = ev.getAction();
    if ((action == MotionEvent.ACTION_MOVE) && (mIsBeingDragged)) {
      return true;
    }
    if (!canScroll()) {
      mIsBeingDragged = false;
      return false;
    }
    final float y = ev.getY();
    final float x = ev.getX();
    switch (action) {
      case MotionEvent.ACTION_MOVE:
        /*
         * mIsBeingDragged == false, otherwise the shortcut would have caught it. Check
         * whether the user has moved far enough from his original down touch.
         */
        /*
         * Locally do absolute value. mLastMotionY is set to the y value
         * of the down event.
         */
        final int yDiff = (int) Math.abs(y - mLastMotionY);
        final int xDiff = (int) Math.abs(x - mLastMotionX);
        if (yDiff > mTouchSlop || xDiff > mTouchSlop) {
          mIsBeingDragged = true;
          final ViewParent parent = getParent();
          if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
        }
        break;

      case MotionEvent.ACTION_DOWN:
        /* Remember location of down touch */
        mLastMotionY = y;
        mLastMotionX = x;

        /*
         * If being flinged and user touches the screen, initiate drag;
         * otherwise don't.  mScroller.isFinished should be false when
         * being flinged.
         */
        mIsBeingDragged = !mScroller.isFinished();
        if (mIsBeingDragged) {
          final ViewParent parent = getParent();
          if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
        }
        break;

      case MotionEvent.ACTION_CANCEL:
      case MotionEvent.ACTION_UP:
        /* Release the drag */
        mIsBeingDragged = false;
        break;
    }

    /*
     * The only time we want to intercept motion events is if we are in the
     * drag mode.
     */
    return mIsBeingDragged;
  }

  /**
   * @return Returns true this TwoDScrollView can be scrolled
   */
  private boolean canScroll() {
    View child = getChildAt(0);
    if (child != null) {
      // Zoom-aware: once pinched in, content that used to fit the viewport can now overflow it.
      int childHeight = scaledChildHeight(child);
      int childWidth = scaledChildWidth(child);
      return (getHeight() < childHeight + getPaddingTop() + getPaddingBottom())
          || (getWidth() < childWidth + getPaddingLeft() + getPaddingRight());
    }
    return false;
  }

  /**
   * When looking for focus in children of a scroll view, need to be a little more careful not to
   * give focus to something that is scrolled off screen.
   *
   * <p>This is more expensive than the default {@link android.view.ViewGroup} implementation,
   * otherwise this behavior might have been made the default.
   */
  @Override
  protected boolean onRequestFocusInDescendants(int direction, Rect previouslyFocusedRect) {
    // convert from forward / backward notation to up / down / left / right
    // (ugh).
    if (direction == View.FOCUS_FORWARD) {
      direction = View.FOCUS_DOWN;
    } else if (direction == View.FOCUS_BACKWARD) {
      direction = View.FOCUS_UP;
    }

    final View nextFocus =
        previouslyFocusedRect == null
            ? FocusFinder.getInstance().findNextFocus(this, null, direction)
            : FocusFinder.getInstance()
                .findNextFocusFromRect(this, previouslyFocusedRect, direction);

    if (nextFocus == null) {
      return false;
    }

    return nextFocus.requestFocus(direction, previouslyFocusedRect);
  }

  @Override
  public void addView(View child) {
    if (getChildCount() > 0) {
      throw new IllegalStateException("TwoDScrollView can host only one direct child");
    }
    super.addView(child);
  }

  @Override
  public void addView(View child, int index) {
    if (getChildCount() > 0) {
      throw new IllegalStateException("TwoDScrollView can host only one direct child");
    }
    super.addView(child, index);
  }

  @Override
  public void addView(View child, ViewGroup.LayoutParams params) {
    if (getChildCount() > 0) {
      throw new IllegalStateException("TwoDScrollView can host only one direct child");
    }
    super.addView(child, params);
  }

  @Override
  public void addView(View child, int index, ViewGroup.LayoutParams params) {
    if (getChildCount() > 0) {
      throw new IllegalStateException("TwoDScrollView can host only one direct child");
    }
    super.addView(child, index, params);
  }

  @Override
  protected void measureChild(View child, int parentWidthMeasureSpec, int parentHeightMeasureSpec) {
    ViewGroup.LayoutParams lp = child.getLayoutParams();
    int childWidthMeasureSpec;
    int childHeightMeasureSpec;

    // Both axes UNSPECIFIED: child can grow freely in both directions for 2D scroll
    childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
    childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);

    child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
  }

  @Override
  protected void measureChildWithMargins(
      View child,
      int parentWidthMeasureSpec,
      int widthUsed,
      int parentHeightMeasureSpec,
      int heightUsed) {
    final MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
    // Both axes UNSPECIFIED: child can grow freely in both directions for 2D scroll
    final int childWidthMeasureSpec =
        MeasureSpec.makeMeasureSpec(lp.leftMargin + lp.rightMargin, MeasureSpec.UNSPECIFIED);
    final int childHeightMeasureSpec =
        MeasureSpec.makeMeasureSpec(lp.topMargin + lp.bottomMargin, MeasureSpec.UNSPECIFIED);

    child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
  }

  /**
   * If rect is off screen, scroll just enough to get it (or at least the first screen size chunk of
   * it) on screen.
   *
   * @param rect The rectangle.
   * @param immediate True to scroll immediately without animation
   * @return true if scrolling was performed
   */
  private boolean scrollToChildRect(Rect rect, boolean immediate) {
    final int delta = computeScrollDeltaToGetChildRectOnScreen(rect);
    final boolean scroll = delta != 0;
    if (scroll) {
      if (immediate) {
        scrollBy(0, delta);
      } else {
        smoothScrollBy(0, delta);
      }
    }
    return scroll;
  }

  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b) {
    super.onLayout(changed, l, t, r, b);
    mIsLayoutDirty = false;
    // Give a child focus if it needs it
    if (mChildToScrollTo != null && isViewDescendantOf(mChildToScrollTo, this)) {
      scrollToChild(mChildToScrollTo);
    }
    mChildToScrollTo = null;

    // Calling this with the present values causes it to re-clam them
    scrollTo(getScrollX(), getScrollY());
  }

  /**
   * Scrolls the view to make the area defined by <code>top</code> and <code>bottom</code> visible.
   * This method attempts to give the focus to a component visible in this area. If no component can
   * be focused in the new visible area, the focus is reclaimed by this scrollview.
   *
   * @param direction the scroll direction: {@link android.view.View#FOCUS_UP} to go upward {@link
   *     android.view.View#FOCUS_DOWN} to downward
   * @param top the top offset of the new area to be made visible
   * @param bottom the bottom offset of the new area to be made visible
   * @return true if the key event is consumed by this method, false otherwise
   */
  private boolean scrollAndFocus(
      int directionY, int top, int bottom, int directionX, int left, int right) {
    boolean handled = true;
    int height = getHeight();
    int containerTop = getScrollY();
    int containerBottom = containerTop + height;
    boolean up = directionY == View.FOCUS_UP;
    int width = getWidth();
    int containerLeft = getScrollX();
    int containerRight = containerLeft + width;
    boolean leftwards = directionX == View.FOCUS_UP;
    View newFocused = findFocusableViewInBounds(up, top, bottom, leftwards, left, right);
    if (newFocused == null) {
      newFocused = this;
    }
    if ((top >= containerTop && bottom <= containerBottom)
        || (left >= containerLeft && right <= containerRight)) {
      handled = false;
    } else {
      int deltaY = up ? (top - containerTop) : (bottom - containerBottom);
      int deltaX = leftwards ? (left - containerLeft) : (right - containerRight);
      doScroll(deltaX, deltaY);
    }
    if (newFocused != findFocus() && newFocused.requestFocus(directionY)) {
      mTwoDScrollViewMovedFocus = true;
      mTwoDScrollViewMovedFocus = false;
    }
    return handled;
  }

  /** Return true if child is an descendant of parent, (or equal to the parent). */
  private boolean isViewDescendantOf(View child, View parent) {
    if (child == parent) {
      return true;
    }

    final ViewParent theParent = child.getParent();
    return (theParent instanceof ViewGroup) && isViewDescendantOf((View) theParent, parent);
  }

  // ===================== Pinch-to-zoom public API ===================== //

  /**
   * Enables or disables pinch-to-zoom, similar to zooming into a photo.
   *
   * <p>غیرفعال کردنش فقط جیچر رو خاموش می‌کنه؛ سطح زوم فعلی دست نمی‌خوره — اگه می‌خوای برگرده رو
   * ۱۰۰٪ هم از {@link #resetZoom()} استفاده کن.
   */
  public void setZoomMod(boolean enabled) {
    zoomManager.setZoomMod(enabled);
  }

  /**
   * @return whether pinch-to-zoom is currently enabled.
   */
  public boolean isZoomMod() {
    return zoomManager.isZoomMod();
  }

  /**
   * Sets the allowed pinch-zoom range, as percentages of the original size (100 = original size).
   *
   * @param minPercent minimum zoom, e.g. 50 for 50%
   * @param maxPercent maximum zoom, e.g. 300 for 300%
   */
  public void setZoomScale(int minPercent, int maxPercent) {
    zoomManager.setZoomScale(minPercent, maxPercent);
  }

  /**
   * @return {@code [minPercent, maxPercent]} currently allowed.
   */
  @NonNull
  public int[] getZoomScale() {
    return zoomManager.getZoomScale();
  }

  public int getMinZoomScale() {
    return zoomManager.getMinZoomScale();
  }

  public int getMaxZoomScale() {
    return zoomManager.getMaxZoomScale();
  }

  /**
   * @return the current zoom level as a percentage (100 = original size).
   */
  public int getCurrentZoomScale() {
    return zoomManager.getCurrentZoomScale();
  }

  /** Programmatically sets the zoom level (percentage), clamped to the configured range. */
  public void setCurrentZoomScale(int percent) {
    zoomManager.setCurrentZoomScale(percent);
  }

  /** Resets the zoom level back to 100%. */
  public void resetZoom() {
    zoomManager.resetZoom();
  }

  /** Direct access to the underlying {@link ZoomManager}, if finer control is needed. */
  @NonNull
  public ZoomManager getZoomManager() {
    return zoomManager;
  }
}
