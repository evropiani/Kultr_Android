package androidx.media3.session;

import android.media.session.PlaybackState;
import androidx.annotation.Nullable;

/**
 * For tests: the playback state a Media3 session hands the Android platform
 * session, which is what System UI builds the player banner from (play,
 * previous, next and the custom actions beside them).
 *
 * Robolectric's platform MediaController is a stand-in that never sees the
 * session, so this reads the state where Media3 publishes it instead. It
 * lives in Media3's package for the package-private way in.
 */
public final class PlatformSessionState {
  private PlatformSessionState() {}

  @Nullable
  public static PlaybackState of(MediaSession session) {
    androidx.media3.session.legacy.PlaybackStateCompat state =
        session
            .getImpl()
            .getMediaSessionLegacyStub()
            .getSessionCompat()
            .getController()
            .getPlaybackState();
    return state == null ? null : state.getPlaybackState();
  }
}
