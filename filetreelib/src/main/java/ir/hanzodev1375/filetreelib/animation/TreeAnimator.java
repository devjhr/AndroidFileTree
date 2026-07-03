package ir.hanzodev1375.filetreelib.animation;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.RecyclerView;

public class TreeAnimator extends DefaultItemAnimator {

  private long expandDuration = 30L;

  public TreeAnimator() {
    setAddDuration(expandDuration);
    setRemoveDuration(expandDuration);
    setMoveDuration(expandDuration);
    setChangeDuration(28L);
  }

  public void setExpandDuration(long ms) {
    this.expandDuration = ms;
    setAddDuration(ms);
    setRemoveDuration(ms);
    setMoveDuration(ms);
  }

  @Override
  public boolean animateAdd(@NonNull RecyclerView.ViewHolder holder) {
    if (holder.itemView.getAlpha() == 0f) {
        dispatchAddFinished(holder);
        return false;
    }
    endAnimation(holder);
    holder.itemView.setAlpha(0f);
    holder.itemView.setScaleX(0.88f);
    holder.itemView.setScaleY(0.88f);
    holder.itemView.animate()
          .alpha(1f)
          .scaleX(1f)
          .scaleY(1f)
          .setDuration(expandDuration)
          .setInterpolator(new DecelerateInterpolator())
          .setListener(new AnimatorListenerAdapter() {
              @Override
              public void onAnimationEnd(Animator animation) {
                  holder.itemView.setAlpha(1f);
                  holder.itemView.setScaleX(1f);
                  holder.itemView.setScaleY(1f);
                  dispatchAddFinished(holder);
              }
          })
         .start();
    return true;
  }

  @Override
  public boolean animateRemove(@NonNull RecyclerView.ViewHolder holder) {
    endAnimation(holder);
    holder.itemView.animate()
          .alpha(0f)
          .scaleX(0.88f)
          .scaleY(0.88f)
          .setDuration(expandDuration)
          .setInterpolator(new DecelerateInterpolator())
          .setListener(new AnimatorListenerAdapter() {
              @Override
              public void onAnimationEnd(Animator animation) {
                  holder.itemView.setAlpha(1f);
                  holder.itemView.setScaleX(1f);
                  holder.itemView.setScaleY(1f);
                  dispatchRemoveFinished(holder);
              }
          })
       .start();
    return true;
  }

    @Override
    public boolean animateMove(@NonNull RecyclerView.ViewHolder holder, 
                               int fromX, int fromY, int toX, int toY) {
        View view = holder.itemView;
        int deltaX = toX - fromX;
        int deltaY = toY - fromY;
        
        if (deltaX == 0 && deltaY == 0) {
            dispatchMoveFinished(holder);
            return false;
        }
        
        endAnimation(holder);
        
        if (deltaX != 0) view.setTranslationX(-deltaX);
        if (deltaY != 0) view.setTranslationY(-deltaY);
        
        view.animate()
             .translationX(0f)
             .translationY(0f)
             .setDuration(expandDuration)
             .setInterpolator(new DecelerateInterpolator())
             .setListener(new AnimatorListenerAdapter() {
                 @Override
                 public void onAnimationEnd(Animator animation) {
                     view.setTranslationX(0f);
                     view.setTranslationY(0f);
                     dispatchMoveFinished(holder);
                 }
             })
             .start();
        return true;
    }

    @Override
    public void endAnimation(@NonNull RecyclerView.ViewHolder holder) {
        holder.itemView.animate().cancel();
        
        holder.itemView.setAlpha(1f);
        holder.itemView.setScaleX(1f);
        holder.itemView.setScaleY(1f);
        holder.itemView.setTranslationX(0f);
        holder.itemView.setTranslationY(0f);
        
        super.endAnimation(holder);
    }
}