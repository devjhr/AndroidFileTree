package ir.hanzodev1375.filetreelib.widget;

import android.content.Context;
import android.graphics.PorterDuff;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import ir.hanzodev1375.filetreelib.R;
import ir.hanzodev1375.filetreelib.clipboard.ClipboardManager;
import ir.hanzodev1375.filetreelib.core.TreeController;
import ir.hanzodev1375.filetreelib.core.TreeNode;

import ir.hanzodev1375.filetreelib.theme.FTThemeManager;
import java.util.List;

public final class SelectionActionPanel extends LinearLayout {

  public interface ActionListener {
    void onCopy(@NonNull List<TreeNode> nodes);

    void onCut(@NonNull List<TreeNode> nodes);

    void onPaste(@Nullable TreeNode targetFolder, @NonNull ClipboardManager clipboard);

    void onRename(@NonNull TreeNode node);

    void onDelete(@NonNull List<TreeNode> nodes);

    void onMore(@NonNull List<TreeNode> nodes, @NonNull View anchor);

    void onSelectionCleared();
  }

  private TextView tvCount;
  private ImageView btnClose;
  private ImageView btnCopy;
  private ImageView btnCut;
  private ImageView btnPaste;
  private ImageView btnRename;
  private ImageView btnSelectAll;
  private ImageView btnDelete;
  private ImageView btnMore;
  private View divview;

  @Nullable private TreeController controller;
  @Nullable private ClipboardManager clipboard;
  @Nullable private ActionListener actionListener;
  @Nullable private FTThemeManager theme;
  private boolean attached = false;

  public SelectionActionPanel(@NonNull Context context) {
    super(context);
    init(context);
  }

  public SelectionActionPanel(@NonNull Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    init(context);
  }

  public SelectionActionPanel(
      @NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init(context);
  }

  private void init(@NonNull Context context) {
    LayoutInflater.from(context).inflate(R.layout.selection_action_panel, this, true);
    setOrientation(VERTICAL);
    theme = new FTThemeManager(context);
    tvCount = findViewById(R.id.txt_selected_count);
    btnClose = findViewById(R.id.btn_close);
    btnCopy = findViewById(R.id.btn_copy);
    btnCut = findViewById(R.id.btn_cut);
    btnPaste = findViewById(R.id.btn_paste);
    btnRename = findViewById(R.id.btn_rename);
    btnSelectAll = findViewById(R.id.btn_selectall);
    btnDelete = findViewById(R.id.btn_delete);
    btnMore = findViewById(R.id.selectionmore);
    divview = findViewById(R.id.divview);
    applyColorFilter(theme);
    wireClicks();
  }

  void applyColorFilter(FTThemeManager theme) {
    setBackgroundColor(theme.getPanelBackgroundColor());
    divview.setBackgroundColor(theme.getPanelDividerColors());
    btnClose.setColorFilter(theme.getPanelColorFilterColor(), PorterDuff.Mode.SRC_IN);
    btnCopy.setColorFilter(theme.getPanelColorFilterColor(), PorterDuff.Mode.SRC_IN);
    btnCut.setColorFilter(theme.getPanelColorFilterColor(), PorterDuff.Mode.SRC_IN);
    btnPaste.setColorFilter(theme.getPanelColorFilterColor(), PorterDuff.Mode.SRC_IN);
    btnRename.setColorFilter(theme.getPanelColorFilterColor(), PorterDuff.Mode.SRC_IN);
    btnSelectAll.setColorFilter(theme.getPanelColorFilterColor(), PorterDuff.Mode.SRC_IN);
    btnDelete.setColorFilter(theme.getPanelColorFilterColor(), PorterDuff.Mode.SRC_IN);
    btnMore.setColorFilter(theme.getPanelColorFilterColor(), PorterDuff.Mode.SRC_IN);
    tvCount.setTextColor(theme.getPanelTextColor());
  }

  @MainThread
  public void attach(@NonNull TreeController ctrl, @NonNull ClipboardManager cb) {
    if (attached) detach();
    this.controller = ctrl;
    this.clipboard = cb;
    this.attached = true;

    ctrl.getSelectionManager()
        .addListener(
            ids -> {
              int count = ids.size();
              if (count == 0) {
                // فقط اگه clipboard خالیه پنل رو مخفی کن
                if (clipboard == null || !clipboard.hasClipboard()) {
                  hide();
                  if (actionListener != null) actionListener.onSelectionCleared();
                }
              } else {
                showSelectionMode(count);
              }
            });
  }

  @MainThread
  public void detach() {
    controller = null;
    clipboard = null;
    attached = false;
    setVisibility(GONE);
  }

  public void setActionListener(@Nullable ActionListener listener) {
    this.actionListener = listener;
  }

  public boolean isSelectionActive() {
    return getVisibility() == VISIBLE;
  }

  // ── حالت انتخاب — همه دکمه‌ها نمایش ──
  private void showSelectionMode(int count) {
    setVisibility(VISIBLE);

    String label = count == 1 ? "1 item selected" : count + " items selected";
    tvCount.setText(label);

    btnCopy.setVisibility(VISIBLE);
    btnCut.setVisibility(VISIBLE);
    btnDelete.setVisibility(VISIBLE);
    btnSelectAll.setVisibility(VISIBLE);
    btnMore.setVisibility(VISIBLE);
    btnClose.setVisibility(VISIBLE);

    if (btnRename != null) btnRename.setVisibility(count == 1 ? VISIBLE : GONE);

    boolean hasPaste = clipboard != null && clipboard.hasClipboard();
    btnPaste.setEnabled(hasPaste);
    btnPaste.setAlpha(hasPaste ? 1f : 0.35f);
  }

  // ── حالت paste-ready — فقط paste و close نمایش ──
  private void showPasteReady() {
    setVisibility(VISIBLE);

    boolean isCut = clipboard != null && clipboard.isCut();
    tvCount.setText(isCut ? "Navigate to destination to move" : "Navigate to destination to paste");

    btnCopy.setVisibility(GONE);
    btnCut.setVisibility(GONE);
    btnDelete.setVisibility(GONE);
    btnSelectAll.setVisibility(GONE);
    btnMore.setVisibility(GONE);
    if (btnRename != null) btnRename.setVisibility(GONE);

    btnPaste.setEnabled(true);
    btnPaste.setAlpha(1f);
    btnClose.setVisibility(VISIBLE);
  }

  private void hide() {
    setVisibility(GONE);
    // ریست ویزیبیلیتی برای دفعه بعد
    btnCopy.setVisibility(VISIBLE);
    btnCut.setVisibility(VISIBLE);
    btnDelete.setVisibility(VISIBLE);
    btnSelectAll.setVisibility(VISIBLE);
    btnMore.setVisibility(VISIBLE);
    btnPaste.setEnabled(false);
    btnPaste.setAlpha(0.35f);
    if (btnRename != null) btnRename.setVisibility(GONE);
  }

  private void wireClicks() {

    btnClose.setOnClickListener(
        v -> {
          if (clipboard != null) clipboard.clear();
          if (controller != null) controller.clearSelection();
          hide();
          if (actionListener != null) actionListener.onSelectionCleared();
        });

    btnCopy.setOnClickListener(
        v -> {
          if (controller == null || clipboard == null) return;
          List<TreeNode> nodes = controller.getSelectedNodes();
          if (nodes.isEmpty()) return;
          clipboard.copy(nodes);
          controller.clearSelection();
          showPasteReady();
          if (actionListener != null) actionListener.onCopy(nodes);
        });

    btnCut.setOnClickListener(
        v -> {
          if (controller == null || clipboard == null) return;
          List<TreeNode> nodes = controller.getSelectedNodes();
          if (nodes.isEmpty()) return;
          clipboard.cut(nodes);
          controller.clearSelection();
          showPasteReady();
          if (actionListener != null) actionListener.onCut(nodes);
        });

    btnPaste.setOnClickListener(
        v -> {
          if (controller == null || clipboard == null) return;
          if (!clipboard.hasClipboard()) return;
          TreeNode target = null;
          for (TreeNode n : controller.getSelectedNodes()) {
            if (n.isFolder()) {
              target = n;
              break;
            }
          }
          if (actionListener != null) actionListener.onPaste(target, clipboard);
        });

    btnSelectAll.setOnClickListener(
        v -> {
          if (controller == null) return;
          controller.getSelectionManager().setVisibleNodes(controller.getVisibleList().snapshot());
          controller.getSelectionManager().selectAll();
        });

    btnDelete.setOnClickListener(
        v -> {
          if (controller == null) return;
          List<TreeNode> nodes = controller.getSelectedNodes();
          if (nodes.isEmpty()) return;
          if (actionListener != null) actionListener.onDelete(nodes);
        });

    if (btnRename != null) {
      btnRename.setOnClickListener(
          v -> {
            if (controller == null) return;
            List<TreeNode> nodes = controller.getSelectedNodes();
            if (nodes.size() == 1 && actionListener != null) {
              actionListener.onRename(nodes.get(0));
            }
          });
    }

    btnMore.setOnClickListener(
        v -> {
          if (controller == null) return;
          List<TreeNode> nodes = controller.getSelectedNodes();
          if (actionListener != null) actionListener.onMore(nodes, v);
        });
  }

  @Override
  protected void onVisibilityChanged(View changedView, int visibility) {
    super.onVisibilityChanged(changedView, visibility);

    if (changedView != this) {
      return;
    }

    animate().cancel();

    if (visibility == VISIBLE) {
      setAlpha(0f);
      setScaleX(0.92f);
      setScaleY(0.92f);
      setTranslationY(dp(16));

      animate()
          .alpha(1f)
          .scaleX(1f)
          .scaleY(1f)
          .translationY(0f)
          .setDuration(320)
          .setInterpolator(new OvershootInterpolator(0.8f))
          .start();
    } else {
      animate()
          .alpha(0f)
          .scaleX(0.95f)
          .scaleY(0.95f)
          .translationY(dp(12))
          .setDuration(180)
          .setInterpolator(new AccelerateInterpolator())
          .start();
    }
  }

  private float dp(float value) {
    return value * getResources().getDisplayMetrics().density;
  }
}
