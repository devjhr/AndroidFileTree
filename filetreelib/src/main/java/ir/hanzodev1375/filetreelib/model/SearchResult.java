package ir.hanzodev1375.filetreelib.model;

import android.text.SpannableString;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a single match produced by {@link com.treeview.search.TreeSearchEngine}.
 *
 * <p>A {@code SearchResult} references the matching node by ID and carries the pre-built {@link
 * SpannableString} with highlight spans so the adapter can render highlighted text without
 * re-running the search algorithm per frame.
 *
 * <p>Match ranges are expressed as (start, end) pairs in the node's display name string, compatible
 * with {@link android.text.style.BackgroundColorSpan} and {@link android.text.style.StyleSpan}.
 */
public final class SearchResult {

  // -------------------------------------------------------------------------
  // Inner: MatchRange
  // -------------------------------------------------------------------------

  /**
   * A half-open [start, end) character range within the node's display name that matched the search
   * query.
   */
  public static final class MatchRange {

    /** Inclusive start index within the display name string. */
    public final int start;

    /** Exclusive end index within the display name string. */
    public final int end;

    public MatchRange(int start, int end) {
      if (start < 0) throw new IllegalArgumentException("start must be >= 0");
      if (end <= start) throw new IllegalArgumentException("end must be > start");
      this.start = start;
      this.end = end;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof MatchRange)) return false;
      MatchRange r = (MatchRange) o;
      return start == r.start && end == r.end;
    }

    @Override
    public int hashCode() {
      return 31 * start + end;
    }

    @NonNull
    @Override
    public String toString() {
      return "[" + start + ", " + end + ")";
    }
  }

  // -------------------------------------------------------------------------
  // Fields
  // -------------------------------------------------------------------------

  /** Stable node ID of the matching node. */
  @NonNull private final String nodeId;

  /**
   * The display name of the node as it was when the search ran. Used to detect stale results if the
   * name changes.
   */
  @NonNull private final String displayName;

  /**
   * All character ranges within {@link #displayName} that matched the query. Sorted by {@link
   * MatchRange#start} ascending.
   */
  @NonNull private final List<MatchRange> matchRanges;

  /**
   * Pre-built spannable version of {@link #displayName} with highlight spans applied. May be {@code
   * null} if the caller has not yet applied spans (deferred rendering).
   */
  @Nullable private SpannableString highlightedName;

  /**
   * Score used to sort results by relevance. Higher = better match. Scoring rubric:
   *
   * <ul>
   *   <li>+100 exact full-name match (case-insensitive)
   *   <li>+50 prefix match at a word boundary
   *   <li>+10 prefix match anywhere
   *   <li>+1 per character that reduces total unmatched span
   * </ul>
   */
  private final int score;

  /**
   * Depth of the node in the tree. Shallower results are ranked higher when scores tie, matching VS
   * Code's behaviour.
   */
  private final int depth;

  // -------------------------------------------------------------------------
  // Constructor
  // -------------------------------------------------------------------------

  private SearchResult(Builder builder) {
    this.nodeId = builder.nodeId;
    this.displayName = builder.displayName;
    this.matchRanges = Collections.unmodifiableList(new ArrayList<>(builder.matchRanges));
    this.highlightedName = builder.highlightedName;
    this.score = builder.score;
    this.depth = builder.depth;
  }

  // -------------------------------------------------------------------------
  // Accessors
  // -------------------------------------------------------------------------

  /** Returns the stable node ID of the matching node. */
  @NonNull
  public String getNodeId() {
    return nodeId;
  }

  /** Returns the display name that was searched. */
  @NonNull
  public String getDisplayName() {
    return displayName;
  }

  /**
   * Returns an unmodifiable list of character ranges within {@link #getDisplayName()} that matched
   * the query, sorted by start position ascending.
   */
  @NonNull
  public List<MatchRange> getMatchRanges() {
    return matchRanges;
  }

  /**
   * Returns the pre-built spannable with highlight spans, or {@code null} if the caller has not yet
   * applied spans.
   */
  @Nullable
  public SpannableString getHighlightedName() {
    return highlightedName;
  }

  /**
   * Attaches a pre-built spannable. Call this from the search engine after applying {@link
   * android.text.style.BackgroundColorSpan} so the adapter can bind it directly without rebuilding
   * every frame.
   */
  public void setHighlightedName(@Nullable SpannableString highlightedName) {
    this.highlightedName = highlightedName;
  }

  /** Returns the relevance score (higher = more relevant). */
  public int getScore() {
    return score;
  }

  /** Returns the depth of the node in the tree (0 = root children). */
  public int getDepth() {
    return depth;
  }

  /**
   * Returns {@code true} if there is at least one match range — i.e. the search engine confirmed a
   * real textual match and not just a filter-pass node that was kept to show a matched descendant.
   */
  public boolean hasDirectMatch() {
    return !matchRanges.isEmpty();
  }

  /**
   * Returns the total number of matched characters across all match ranges. Used by the scoring
   * algorithm.
   */
  public int totalMatchedChars() {
    int total = 0;
    for (MatchRange r : matchRanges) {
      total += (r.end - r.start);
    }
    return total;
  }

  // -------------------------------------------------------------------------
  // Object
  // -------------------------------------------------------------------------

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SearchResult)) return false;
    SearchResult other = (SearchResult) o;
    return score == other.score
        && depth == other.depth
        && nodeId.equals(other.nodeId)
        && displayName.equals(other.displayName)
        && matchRanges.equals(other.matchRanges);
  }

  @Override
  public int hashCode() {
    int result = nodeId.hashCode();
    result = 31 * result + displayName.hashCode();
    result = 31 * result + score;
    return result;
  }

  @NonNull
  @Override
  public String toString() {
    return "SearchResult{"
        + "nodeId='"
        + nodeId
        + '\''
        + ", displayName='"
        + displayName
        + '\''
        + ", score="
        + score
        + ", ranges="
        + matchRanges
        + '}';
  }

  // -------------------------------------------------------------------------
  // Builder
  // -------------------------------------------------------------------------

  /** Fluent builder for {@link SearchResult}. */
  public static final class Builder {

    @NonNull private final String nodeId;
    @NonNull private final String displayName;
    @NonNull private final List<MatchRange> matchRanges = new ArrayList<>();
    @Nullable private SpannableString highlightedName;
    private int score = 0;
    private int depth = 0;

    public Builder(@NonNull String nodeId, @NonNull String displayName) {
      if (nodeId.isEmpty()) throw new IllegalArgumentException("nodeId must not be empty");
      if (displayName.isEmpty())
        throw new IllegalArgumentException("displayName must not be empty");
      this.nodeId = nodeId;
      this.displayName = displayName;
    }

    /** Adds a character-range match. Ranges should be within {@code [0, displayName.length())}. */
    public Builder addRange(int start, int end) {
      matchRanges.add(new MatchRange(start, end));
      return this;
    }

    public Builder highlightedName(@Nullable SpannableString spannable) {
      this.highlightedName = spannable;
      return this;
    }

    public Builder score(int score) {
      this.score = score;
      return this;
    }

    public Builder depth(int depth) {
      this.depth = depth;
      return this;
    }

    public SearchResult build() {
      // Sort ranges by start position so downstream consumers don't have to.
      Collections.sort(matchRanges, (a, b) -> Integer.compare(a.start, b.start));
      return new SearchResult(this);
    }
  }
}
