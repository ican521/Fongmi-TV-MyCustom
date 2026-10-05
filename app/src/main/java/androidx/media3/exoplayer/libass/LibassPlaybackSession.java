package androidx.media3.exoplayer.libass;

import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.extractor.ExtractorsFactory;
import androidx.media3.extractor.text.SubtitleParser;

/** Stub: libass integration is not available in this build. isAvailable() returns false so the
 *  normal media3 subtitle pipeline is used instead. */
public class LibassPlaybackSession {

  public LibassPlaybackSession(LibassConfiguration configuration, boolean libassEnabled) {
  }

  /** Always false in this stub build -> libass path is skipped at runtime. */
  public boolean isAvailable() {
    return false;
  }

  public void setPreloadMediaItem(MediaItem mediaItem) {
  }

  public void setBottomPositionFraction(float fraction) {
  }

  public void setSecondaryBottomPositionFraction(float fraction) {
  }

  public void setFontScale(float fontScale, boolean apply) {
  }

  public void close() {
  }

  /** Only ever invoked when isAvailable()==true; returns null (never reached in this stub). */
  public Renderer createClockRenderer() {
    return null;
  }

  public MediaComponents createMediaComponents(MediaItem mediaItem, ExtractorsFactory extractorsFactory) {
    return new MediaComponents(extractorsFactory, null);
  }

  public static final class MediaComponents {

    public final ExtractorsFactory extractorsFactory;
    public final SubtitleParser.Factory subtitleParserFactory;

    public MediaComponents(ExtractorsFactory extractorsFactory, SubtitleParser.Factory subtitleParserFactory) {
      this.extractorsFactory = extractorsFactory;
      this.subtitleParserFactory = subtitleParserFactory;
    }
  }
}
