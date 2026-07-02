package ir.hanzodev1375.filetreelib.widget;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import ir.hanzodev1375.filetreelib.R;
import ir.hanzodev1375.filetreelib.adapter.TreeAdapter;
import ir.hanzodev1375.filetreelib.animation.TreeAnimator;
import ir.hanzodev1375.filetreelib.core.TreeController;
import ir.hanzodev1375.filetreelib.core.TreeNode;
import ir.hanzodev1375.filetreelib.decoration.TreeDecoration;
import ir.hanzodev1375.filetreelib.drag.DragManager;
import ir.hanzodev1375.filetreelib.theme.ThemeManager;
import ir.hanzodev1375.filetreelib.widget.TwoDScrollView;

public final class TreeView extends RecyclerView {

  private TreeAdapter treeAdapter;
  private ThemeManager theme;
  private TreeDecoration treeDecoration;
  private TreeAnimator animator;
  private int focusedPosition = -1;

  private boolean showTreeLines = true;
  private boolean rainbowIndentGuides = false;
  @Nullable private int[] rainbowIndentGuideColors = null; // null => TreeDecoration's defaults
  private boolean animateExpand = true;
  private long animDuration = 180L;

  @Nullable private ItemTouchHelper dragTouchHelper = null;
  @Nullable private DragManager dragManager = null;

  public TreeView(@NonNull Context context) {
    super(context);
    init(context, null);
  }

  public TreeView(@NonNull Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    init(context, attrs);
  }

  public TreeView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    init(context, attrs);
  }

  private int screenWidth = 0;

  private void init(@NonNull Context context, @Nullable AttributeSet attrs) {
    if (attrs != null) {
      TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.TreeView);
      showTreeLines = a.getBoolean(R.styleable.TreeView_tv_showTreeLines, true);
      rainbowIndentGuides = a.getBoolean(R.styleable.TreeView_tv_rainbowIndentGuides, false);
      animateExpand = a.getBoolean(R.styleable.TreeView_tv_animateExpand, true);
      animDuration = a.getInt(R.styleable.TreeView_tv_animateDuration, 180);
      a.recycle();
    }

    screenWidth = context.getResources().getDisplayMetrics().widthPixels;

    setLayoutManager(
        new LinearLayoutManager(context) {
          @Override
          public boolean canScrollHorizontally() {
            // Horizontal scroll is handled by TwoDScrollView wrapper, not RecyclerView
            return false;
          }

          @Override
          public void onMeasure(
              @NonNull RecyclerView.Recycler recycler,
              @NonNull RecyclerView.State state,
              int widthSpec,
              int heightSpec) {
            // Give children UNSPECIFIED width so they can grow beyond the screen
            widthSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
            super.onMeasure(recycler, state, widthSpec, heightSpec);
          }
        });
    setHasFixedSize(false);

    // Force each item to be at least as wide as the screen.
    addOnChildAttachStateChangeListener(
        new OnChildAttachStateChangeListener() {
          @Override
          public void onChildViewAttachedToWindow(@NonNull View view) {
            int minW = Math.max(screenWidth, getWidth());
            if (view.getMinimumWidth() != minW) {
              view.setMinimumWidth(minW);
            }
          }

          @Override
          public void onChildViewDetachedFromWindow(@NonNull View view) {}
        });

    animator = new TreeAnimator();
    animator.setExpandDuration(animDuration);
    if (animateExpand) setItemAnimator(animator);
    else setItemAnimator(null);
  }

  public void setup(@NonNull TreeController controller, @NonNull ThemeManager themeManager) {
    this.theme = themeManager;
    this.treeAdapter = new TreeAdapter(getContext(), controller, themeManager);
    setAdapter(treeAdapter);

    if (treeDecoration != null) removeItemDecoration(treeDecoration);
    treeDecoration = new TreeDecoration(themeManager);
    treeDecoration.setShowLines(showTreeLines);
    treeDecoration.setRainbowIndentGuides(rainbowIndentGuides);
    if (rainbowIndentGuideColors != null) {
      treeDecoration.setRainbowColors(rainbowIndentGuideColors);
    }
    addItemDecoration(treeDecoration);

    setFocusable(true);
    setDescendantFocusability(FOCUS_BEFORE_DESCENDANTS);

    controller.getExpandManager().rebuildVisibleList(controller.getModel().getRoot());
    treeAdapter.submitNewList(controller.getVisibleList().snapshot());

    // Sync adapter whenever the model changes (delete, rename, create, move).
    // Debounced: compound ops like cut (remove + insert) fire two events back-to-back.
    // removeCallbacks+post ensures only one rebuild runs after both events settle.
    final Handler modelSyncHandler = new Handler(Looper.getMainLooper());
    final Runnable modelSyncRunnable =
        () -> {
          controller.getExpandManager().rebuildVisibleList(controller.getModel().getRoot());
          treeAdapter.submitNewList(controller.getVisibleList().snapshot());
        };

    controller
        .getModel()
        .addListener(
            new ir.hanzodev1375.filetreelib.core.TreeModel.TreeModelListener() {
              @Override
              public void onNodesInserted(
                  @NonNull ir.hanzodev1375.filetreelib.core.TreeNode parent,
                  @NonNull java.util.List<ir.hanzodev1375.filetreelib.core.TreeNode> insertedNodes,
                  int startIndex) {
                modelSyncHandler.removeCallbacks(modelSyncRunnable);
                modelSyncHandler.post(modelSyncRunnable);
              }

              @Override
              public void onNodesRemoved(
                  @NonNull ir.hanzodev1375.filetreelib.core.TreeNode parent,
                  @NonNull java.util.List<ir.hanzodev1375.filetreelib.core.TreeNode> removedNodes,
                  int startIndex) {
                modelSyncHandler.removeCallbacks(modelSyncRunnable);
                modelSyncHandler.post(modelSyncRunnable);
              }

              @Override
              public void onNodesChanged(
                  @NonNull java.util.List<ir.hanzodev1375.filetreelib.core.TreeNode> changedNodes) {
                // Name/icon change only — no structure rebuild needed
                treeAdapter.submitNewList(controller.getVisibleList().snapshot());
              }

              @Override
              public void onStructureChanged() {
                modelSyncHandler.removeCallbacks(modelSyncRunnable);
                modelSyncHandler.post(modelSyncRunnable);
              }
            });
  }

  /** Attach the DragManager — call after setup(). */
  public void attachDragManager(@NonNull DragManager dragManager) {
    if (treeAdapter == null) return;
    if (dragTouchHelper != null) dragTouchHelper.attachToRecyclerView(null);
    dragTouchHelper = dragManager.buildItemTouchHelper(treeAdapter);
    dragTouchHelper.attachToRecyclerView(this);
    this.dragManager = dragManager;
  }

  public void detachDragManager() {
    if (dragTouchHelper != null) {
      dragTouchHelper.attachToRecyclerView(null);
      dragTouchHelper = null;
    }
    this.dragManager = null;
  }

  @Override
  public boolean dispatchTouchEvent(MotionEvent ev) {
    // dispatchTouchEvent always sees every event, regardless of which
    // OnItemTouchListener (e.g. ItemTouchHelper's own) ends up claiming the
    // gesture — this is the only reliable place to feed DragManager's
    // edge auto-scroll tracking.
    if (dragManager != null) {
      dragManager.onRawTouchEvent(this, ev);
    }
    return super.dispatchTouchEvent(ev);
  }

  @Nullable
  public TreeAdapter getTreeAdapter() {
    return treeAdapter;
  }

  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    if (treeAdapter == null) return super.dispatchKeyEvent(event);
    if (event.getAction() != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event);

    int count = treeAdapter.getItemCount();
    if (count == 0) return super.dispatchKeyEvent(event);

    switch (event.getKeyCode()) {
      case KeyEvent.KEYCODE_DPAD_DOWN:
        focusedPosition = Math.min(focusedPosition + 1, count - 1);
        scrollToPosition(focusedPosition);
        return true;

      case KeyEvent.KEYCODE_DPAD_UP:
        focusedPosition = Math.max(focusedPosition - 1, 0);
        scrollToPosition(focusedPosition);
        return true;

      case KeyEvent.KEYCODE_DPAD_RIGHT:
      case KeyEvent.KEYCODE_DPAD_CENTER:
      case KeyEvent.KEYCODE_ENTER:
        {
          if (focusedPosition < 0) return true;
          TreeNode node = treeAdapter.getNode(focusedPosition);
          if (node != null) {
            ViewHolder vh = findViewHolderForAdapterPosition(focusedPosition);
            if (vh != null) vh.itemView.performClick();
          }
          return true;
        }

      case KeyEvent.KEYCODE_DPAD_LEFT:
        {
          if (focusedPosition < 0) return true;
          TreeNode node = treeAdapter.getNode(focusedPosition);
          if (node != null && node.isExpanded()) {
            ViewHolder vh = findViewHolderForAdapterPosition(focusedPosition);
            if (vh != null) {
              View arrow = vh.itemView.findViewById(R.id.tv_arrow);
              if (arrow != null) arrow.performClick();
            }
          }
          return true;
        }

      case KeyEvent.KEYCODE_HOME:
        focusedPosition = 0;
        scrollToPosition(0);
        return true;

      case KeyEvent.KEYCODE_MOVE_END:
        focusedPosition = count - 1;
        scrollToPosition(focusedPosition);
        return true;
    }
    return super.dispatchKeyEvent(event);
  }

  /**
   * Returns this TreeView wrapped inside a TwoDScrollView for both vertical and horizontal
   * scrolling. Call this instead of using TreeView directly in your layout, or wrap it in XML
   * manually.
   *
   * <p>Usage: View scrollable = treeView.getScrollableView(); container.addView(scrollable);
   */
  public View getScrollableView() {
    TwoDScrollView scrollView = new TwoDScrollView(getContext());
    scrollView.setLayoutParams(
        new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    if (getParent() != null) {
      ((ViewGroup) getParent()).removeView(this);
    }
    setLayoutParams(
        new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
    scrollView.addView(this);
    return scrollView;
  }

  public void setShowTreeLines(boolean show) {
    showTreeLines = show;
    if (treeDecoration != null) treeDecoration.setShowLines(show);
    invalidateItemDecorations();
  }

  /**
   * Enables or disables rainbow-colored indent guide lines — each depth level cycles through a
   * different color instead of one flat line color, similar to bracket-pair colorization in VS
   * Code. Off by default. Safe to call before {@link #setup} (the setting is applied once the
   * decoration is created).
   */
  public void setRainbowIndentGuides(boolean enabled) {
    rainbowIndentGuides = enabled;
    if (treeDecoration != null) {
      treeDecoration.setRainbowIndentGuides(enabled);
      invalidateItemDecorations();
    }
  }

  /**
   * @return whether rainbow indent guides are currently enabled. Off by default.
   */
  public boolean isRainbowIndentGuides() {
    return rainbowIndentGuides;
  }

  /**
   * Overrides the color palette used by rainbow indent guides. Depth 1 uses {@code colors[0]},
   * depth 2 uses {@code colors[1]}, wrapping back to {@code colors[0]} once exhausted. Safe to call
   * before {@link #setup} (the colors are applied once the decoration is created).
   *
   * @param colors non-empty array of ARGB colors
   */
  public void setRainbowIndentGuideColors(@NonNull int[] colors) {
    rainbowIndentGuideColors = colors;
    if (treeDecoration != null) {
      treeDecoration.setRainbowColors(colors);
      invalidateItemDecorations();
    }
  }

  public void setAnimateExpand(boolean animate) {
    animateExpand = animate;
    setItemAnimator(animate ? animator : null);
  }
}
