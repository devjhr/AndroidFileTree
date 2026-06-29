package ir.hanzodev1375.filetreelib.animation;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.RecyclerView;

/** Custom RecyclerView item animator with alpha + translate for expand/collapse. */
public class TreeAnimator extends DefaultItemAnimator {

  private long expandDuration = 180L;

  public TreeAnimator() {
    setAddDuration(expandDuration);
    setRemoveDuration(expandDuration);
    setMoveDuration(expandDuration);
    setChangeDuration(80L);
  }

  public void setExpandDuration(long ms) {
    this.expandDuration = ms;
    setAddDuration(ms);
    setRemoveDuration(ms);
    setMoveDuration(ms);
  }

  @Override
  public boolean animateAdd(@NonNull RecyclerView.ViewHolder holder) {
    View view = holder.itemView;
    view.setAlpha(0f);
    view.setTranslationY(-8f);
    view.animate()
        .alpha(1f)
        .translationY(0f)
        .setDuration(expandDuration)
        .setListener(
            new AnimatorListenerAdapter() {
              @Override
              public void onAnimationEnd(Animator animation) {
                dispatchAddFinished(holder);
              }
            })
        .start();
    return true;
  }

  @Override
  public boolean animateRemove(@NonNull RecyclerView.ViewHolder holder) {
    View view = holder.itemView;
    view.animate()
        .alpha(0f)
        .translationY(-8f)
        .setDuration(expandDuration)
        .setListener(
            new AnimatorListenerAdapter() {
              @Override
              public void onAnimationEnd(Animator animation) {
                view.setAlpha(1f);
                view.setTranslationY(0f);
                dispatchRemoveFinished(holder);
              }
            })
        .start();
    return true;
  }
}
