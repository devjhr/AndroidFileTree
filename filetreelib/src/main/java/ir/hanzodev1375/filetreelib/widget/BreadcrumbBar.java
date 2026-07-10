package ir.hanzodev1375.filetreelib.widget;

import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import ir.hanzodev1375.filetreelib.core.TreeNode;
import ir.hanzodev1375.filetreelib.core.TreePath;
import ir.hanzodev1375.filetreelib.theme.FTThemeManager;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Horizontal, tappable path bar meant to sit above the tree (see {@code layout_nodeview.xml}).
 *
 * <p>Backed by a {@link RecyclerView} with a horizontal {@link LinearLayoutManager} instead of a
 * {@link android.widget.HorizontalScrollView} — rows are recycled instead of all being inflated and
 * measured up front, and touch/fling/nested-scroll handling comes from RecyclerView's own (already
 * correct) implementation rather than hand-rolled scrolling code.
 *
 * <p>Renders as one continuous breadcrumb built from two sources:
 *
 * <ul>
 *   <li><b>Root segments</b> — one per folder in the tree's root path, set via {@link
 *       #setRootPath(String)} (e.g. {@code "/storage/emulated/0/Documents"} renders as "storage
 *       &gt; emulated &gt; 0 &gt; Documents"). Tapping one calls {@link
 *       OnSegmentClickListener#onRootSegmentClick(String)} with the path up to and including it —
 *       the intent being "re-root the tree here".
 *   <li><b>Selection segments</b> — appended after the root, showing the path from the tree's root
 *       down to whatever node was last passed to {@link #setSelectedNode}. Tapping one calls {@link
 *       OnSegmentClickListener#onNodeSegmentClick(TreeNode)} — the intent being "reveal this node
 *       in the currently loaded tree" (no reload).
 * </ul>
 *
 * <p>This view only renders and reports taps; it does not itself reload the tree or scroll the tree
 * into view — see {@code FileTreeView}'s wiring for that.
 *
 * <h3>Usage</h3>
 *
 * <pre>{@code
 * breadcrumbBar.setTheme(theme);
 * breadcrumbBar.setOnSegmentClickListener(new BreadcrumbBar.OnSegmentClickListener() {
 *   @Override public void onRootSegmentClick(@NonNull String path) {
 *     setNodePath(path);
 *     loadTree(); // re-root
 *   }
 *   @Override public void onNodeSegmentClick(@NonNull TreeNode node) {
 *     controller.revealNode(node); // expand + scroll to it in the current tree
 *     breadcrumbBar.setSelectedNode(node, treeRootNode);
 *   }
 * });
 *
 * // when the tree (re)loads:
 * breadcrumbBar.setRootPath(rootDir.getAbsolutePath());
 *
 * // whenever a row is clicked:
 * breadcrumbBar.setSelectedNode(clickedNode, treeRootNode);
 * }</pre>
 */
public final class BreadcrumbBar extends RecyclerView {

  /** Notified when the user taps a segment. */
  public interface OnSegmentClickListener {

    /** A root-path segment was tapped; {@code path} is the absolute path up to it, inclusive. */
    void onRootSegmentClick(@NonNull String path);

    /** A selection-path segment was tapped; {@code node} is the tree node it corresponds to. */
    void onNodeSegmentClick(@NonNull TreeNode node);
  }

  // -------------------------------------------------------------------------
  // Row model
  // -------------------------------------------------------------------------

  private static final int VIEW_TYPE_SEGMENT = 0;
  private static final int VIEW_TYPE_SEPARATOR = 1;

  /** The glyph drawn between segments. Change this if you want a different look. */
  private static final String SEPARATOR_GLYPH = "\u203A"; // ›

  /** One row in the breadcrumb's internal list — either a tappable segment or a separator. */
  private static final class Row {
    final int viewType;
    @Nullable final String label; // segment display text; null for separators
    final boolean isRoot; // true = root-path segment, false = node segment
    final boolean isCurrent; // last/current segment -> rendered bold, full opacity
    @Nullable final String rootPath; // full path up to & including this segment (root rows only)
    @Nullable final TreeNode node; // associated node (node rows only)

    private Row(
        int viewType,
        @Nullable String label,
        boolean isRoot,
        boolean isCurrent,
        @Nullable String rootPath,
        @Nullable TreeNode node) {
      this.viewType = viewType;
      this.label = label;
      this.isRoot = isRoot;
      this.isCurrent = isCurrent;
      this.rootPath = rootPath;
      this.node = node;
    }

    static Row rootSegment(@NonNull String label, boolean isCurrent, @NonNull String rootPath) {
      return new Row(VIEW_TYPE_SEGMENT, label, true, isCurrent, rootPath, null);
    }

    static Row nodeSegment(boolean isCurrent, @NonNull TreeNode node) {
      return new Row(VIEW_TYPE_SEGMENT, node.getName(), false, isCurrent, null, node);
    }

    static Row separator() {
      return new Row(VIEW_TYPE_SEPARATOR, null, false, false, null, null);
    }
  }

  // -------------------------------------------------------------------------
  // Fields
  // -------------------------------------------------------------------------

  @NonNull private final List<Row> rows = new ArrayList<>();
  @NonNull private final RowAdapter adapter = new RowAdapter();

  @Nullable private FTThemeManager theme;
  @Nullable private OnSegmentClickListener listener;

  @NonNull private String rootPath = "";
  @Nullable private TreeNode selectedNode;
  @Nullable private TreeNode treeRoot;

  // -------------------------------------------------------------------------
  // Constructors
  // -------------------------------------------------------------------------

  public BreadcrumbBar(@NonNull Context context) {
    super(context);
    init();
  }

  public BreadcrumbBar(@NonNull Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  public BreadcrumbBar(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init();
  }

  private void init() {
    // Path hierarchy always reads left-to-right, regardless of the app's locale direction
    // (same convention as URLs and VS Code's own breadcrumb) — force LTR so an RTL host app
    // doesn't reverse the segment order.
    setLayoutDirection(LAYOUT_DIRECTION_LTR);
    setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
    setAdapter(adapter);
    setHasFixedSize(false); // row count changes as the path gets longer/shorter
    setOverScrollMode(OVER_SCROLL_IF_CONTENT_SCROLLS);
  }

  // -------------------------------------------------------------------------
  // Public API
  // -------------------------------------------------------------------------

  /** Applies theme colors. Safe to call again later if the theme changes. */
  public void setTheme(@NonNull FTThemeManager theme) {
    this.theme = theme;
    setBackgroundColor(theme.getPanelBackgroundColor());
    adapter.notifyDataSetChanged();
  }

  public void setOnSegmentClickListener(@Nullable OnSegmentClickListener listener) {
    this.listener = listener;
  }

  /**
   * Sets the tree's root path (e.g. from {@code FileTreeView.getNodePath()}) and splits it into
   * tappable segments. Also clears any previously selected node, since it belonged to whatever tree
   * was loaded before this root.
   */
  public void setRootPath(@NonNull String absolutePath) {
    this.rootPath = absolutePath;
    this.selectedNode = null;
    this.treeRoot = null;
    rebuild();
  }

  /**
   * Extends the breadcrumb with the path from the tree's root down to {@code node}. Pass {@code
   * null} to collapse back to showing just the root path.
   *
   * @param node the newly selected/opened node, or null to clear the extension
   * @param treeRoot the tree's own first-level node (the one representing {@link #rootPath} itself)
   *     — needed so the walk up from {@code node} knows where to stop, and so that segment isn't
   *     rendered twice
   */
  public void setSelectedNode(@Nullable TreeNode node, @Nullable TreeNode treeRoot) {
    this.selectedNode = node;
    this.treeRoot = treeRoot;
    rebuild();
  }

  // -------------------------------------------------------------------------
  // Row building
  // -------------------------------------------------------------------------

  private void rebuild() {
    rows.clear();

    List<String> rootSegments = splitPath(rootPath);
    List<TreeNode> extension = resolveExtension();

    StringBuilder pathSoFar = new StringBuilder();
    for (int i = 0; i < rootSegments.size(); i++) {
      String seg = rootSegments.get(i);
      pathSoFar.append(File.separator).append(seg);
      if (!rows.isEmpty()) rows.add(Row.separator());
      boolean isCurrent = extension.isEmpty() && i == rootSegments.size() - 1;
      rows.add(Row.rootSegment(seg, isCurrent, pathSoFar.toString()));
    }

    for (int i = 0; i < extension.size(); i++) {
      TreeNode node = extension.get(i);
      if (!rows.isEmpty()) rows.add(Row.separator());
      boolean isCurrent = i == extension.size() - 1;
      rows.add(Row.nodeSegment(isCurrent, node));
    }

    adapter.notifyDataSetChanged();
    if (!rows.isEmpty()) {
      post(() -> smoothScrollToPosition(rows.size() - 1));
    }
  }

  /** Resolves the node-chain to render after the root segments, skipping the root itself. */
  @NonNull
  private List<TreeNode> resolveExtension() {
    if (selectedNode == null || treeRoot == null) return new ArrayList<>();
    List<TreeNode> chain;
    try {
      chain = TreePath.fromNode(selectedNode).getSegments();
    } catch (RuntimeException e) {
      // Node fell out of the tree between being clicked and this rebuild (e.g. deleted) —
      // just fall back to showing the root path alone.
      return new ArrayList<>();
    }
    int startIndex = chain.size(); // default: treeRoot not found -> no extension
    for (int i = 0; i < chain.size(); i++) {
      if (chain.get(i).getId().equals(treeRoot.getId())) {
        startIndex = i + 1;
        break;
      }
    }
    return chain.subList(Math.min(startIndex, chain.size()), chain.size());
  }

  @NonNull
  private List<String> splitPath(@NonNull String path) {
    List<String> parts = new ArrayList<>();
    for (String p : path.split(File.separator)) {
      if (!p.isEmpty()) parts.add(p);
    }
    return parts;
  }

  // -------------------------------------------------------------------------
  // Adapter / ViewHolders
  // -------------------------------------------------------------------------

  /**
   * Non-static on purpose: it's permanently 1:1 with the outer {@code BreadcrumbBar} (created once
   * in {@link #init()}, never handed to another view), so there's no lifecycle mismatch to worry
   * about, and keeping it inner gives direct access to {@link #theme}, {@link #listener} and {@link
   * #dp(int)} without threading them through constructors.
   */
  private final class RowAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    @Override
    public int getItemViewType(int position) {
      return rows.get(position).viewType;
    }

    @Override
    public int getItemCount() {
      return rows.size();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
      if (viewType == VIEW_TYPE_SEPARATOR) {
        return new SeparatorViewHolder(parent);
      }
      return new SegmentViewHolder(parent);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
      Row row = rows.get(position);
      if (holder instanceof SegmentViewHolder) {
        ((SegmentViewHolder) holder).bind(row);
      } else {
        ((SeparatorViewHolder) holder).applyTheme();
      }
    }
  }

  /** A single tappable path segment. */
  private class SegmentViewHolder extends RecyclerView.ViewHolder {

    @NonNull private final TextView label;
    @Nullable private Row boundRow;

    SegmentViewHolder(@NonNull ViewGroup parent) {
      super(new TextView(parent.getContext()));
      label = (TextView) itemView;
      label.setLayoutParams(
          new RecyclerView.LayoutParams(
              RecyclerView.LayoutParams.WRAP_CONTENT, RecyclerView.LayoutParams.WRAP_CONTENT));
      label.setSingleLine(true);
      label.setTextSize(13f);
      label.setPadding(dp(6), dp(4), dp(6), dp(4));
      label.setBackgroundResource(resolveSelectableBackground());
      label.setClickable(true);
      label.setFocusable(true);
      label.setOnClickListener(
          v -> {
            if (boundRow == null || listener == null) return;
            if (boundRow.isRoot) {
              listener.onRootSegmentClick(boundRow.rootPath);
            } else {
              listener.onNodeSegmentClick(boundRow.node);
            }
          });
    }

    void bind(@NonNull Row row) {
      boundRow = row;
      label.setText(row.label);
      label.setTypeface(label.getTypeface(), row.isCurrent ? Typeface.BOLD : Typeface.NORMAL);
      label.setAlpha(row.isCurrent ? 1f : 0.75f);
      if (theme != null) label.setTextColor(theme.getTextColor());
    }
  }

  /** A static "›" between two segments — not clickable, nothing to bind besides theme color. */
  private final class SeparatorViewHolder extends RecyclerView.ViewHolder {

    @NonNull private final TextView glyph;

    SeparatorViewHolder(@NonNull ViewGroup parent) {
      super(new TextView(parent.getContext()));
      glyph = (TextView) itemView;
      glyph.setLayoutParams(
          new RecyclerView.LayoutParams(
              RecyclerView.LayoutParams.WRAP_CONTENT, RecyclerView.LayoutParams.WRAP_CONTENT));
      glyph.setSingleLine(true);
      glyph.setText(SEPARATOR_GLYPH);
      glyph.setTextSize(13f);
      glyph.setPadding(0, dp(4), 0, dp(4));
      glyph.setAlpha(0.4f);
    }

    void applyTheme() {
      if (theme != null) glyph.setTextColor(theme.getTextColor());
    }
  }

  // -------------------------------------------------------------------------
  // Small helpers
  // -------------------------------------------------------------------------

  private int resolveSelectableBackground() {
    TypedValue outValue = new TypedValue();
    getContext()
        .getTheme()
        .resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
    return outValue.resourceId;
  }

  private int dp(int value) {
    float density = getResources().getDisplayMetrics().density;
    return Math.round(value * density);
  }
}
