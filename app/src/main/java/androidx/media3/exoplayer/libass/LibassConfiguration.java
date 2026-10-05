package androidx.media3.exoplayer.libass;

/** Stub: libass integration is not available in this build. Disables libass subtitle rendering. */
public final class LibassConfiguration {

  private LibassConfiguration(Builder builder) {
  }

  public static final class Builder {

    public Builder setFontConfig(String fontConfig) {
      return this;
    }

    public Builder setFontsDirectory(String fontsDirectory) {
      return this;
    }

    public Builder setDefaultFontFamily(String defaultFontFamily) {
      return this;
    }

    public Builder setMaximumRenderPixels(int maximumRenderPixels) {
      return this;
    }

    public Builder setMaximumGlyphCount(int maximumGlyphCount) {
      return this;
    }

    public Builder setMaximumBitmapCacheSizeMb(int maximumBitmapCacheSizeMb) {
      return this;
    }

    public LibassConfiguration build() {
      return new LibassConfiguration(this);
    }
  }
}
