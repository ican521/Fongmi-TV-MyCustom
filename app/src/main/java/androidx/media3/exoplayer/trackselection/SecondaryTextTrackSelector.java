package androidx.media3.exoplayer.trackselection;

import android.content.Context;

import androidx.annotation.NonNull;

/** Stub: wraps a delegate TrackSelector.Factory; secondary text selection is a pass-through no-op. */
public class SecondaryTextTrackSelector {

  private SecondaryTextTrackSelector() {
  }

  public static final class Factory implements TrackSelector.Factory {

    private final TrackSelector.Factory delegate;

    public Factory(TrackSelector.Factory factory) {
      this.delegate = factory;
    }

    @NonNull
    @Override
    public TrackSelector createTrackSelector(@NonNull Context context) {
      return delegate.createTrackSelector(context);
    }
  }
}
