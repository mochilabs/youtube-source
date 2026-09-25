package dev.lavalink.youtube.track.format;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.StringJoiner;

import static com.sedmelluq.discord.lavaplayer.container.Formats.MIME_AUDIO_WEBM;

public class TrackFormats {
    private static final Logger log = LoggerFactory.getLogger(TrackFormats.class);

    private final List<StreamFormat> formats;
    private final String playerScriptUrl;
    private final boolean allowAutoDubbedAudio;
    private final String serverAbrStreamingUrl;
    private final String videoPlaybackUstreamerConfig;
    private final String poToken;

    public TrackFormats(@NotNull List<StreamFormat> formats,
                        @NotNull String playerScriptUrl) {
        this(formats, playerScriptUrl, true, null, null, null);
    }

    public TrackFormats(@NotNull List<StreamFormat> formats,
                        @NotNull String playerScriptUrl,
                        boolean allowAutoDubbedAudio) {
        this(formats, playerScriptUrl, allowAutoDubbedAudio, null, null, null);
    }

    public TrackFormats(@NotNull List<StreamFormat> formats,
                        @NotNull String playerScriptUrl,
                        @Nullable String serverAbrStreamingUrl,
                        @Nullable String videoPlaybackUstreamerConfig) {
        this(formats, playerScriptUrl, true, serverAbrStreamingUrl, videoPlaybackUstreamerConfig, null);
    }

    public TrackFormats(@NotNull List<StreamFormat> formats,
                        @NotNull String playerScriptUrl,
                        @Nullable String serverAbrStreamingUrl,
                        @Nullable String videoPlaybackUstreamerConfig,
                        @Nullable String poToken) {
        this(formats, playerScriptUrl, true, serverAbrStreamingUrl, videoPlaybackUstreamerConfig, poToken);
    }

    public TrackFormats(@NotNull List<StreamFormat> formats,
                        @NotNull String playerScriptUrl,
                        boolean allowAutoDubbedAudio,
                        @Nullable String serverAbrStreamingUrl,
                        @Nullable String videoPlaybackUstreamerConfig,
                        @Nullable String poToken) {
        this.formats = formats;
        this.playerScriptUrl = playerScriptUrl;
        this.allowAutoDubbedAudio = allowAutoDubbedAudio;
        this.serverAbrStreamingUrl = serverAbrStreamingUrl;
        this.videoPlaybackUstreamerConfig = videoPlaybackUstreamerConfig;
        this.poToken = poToken;
    }

    @NotNull
    public List<StreamFormat> getFormats() {
        return this.formats;
    }

    @NotNull
    public String getPlayerScriptUrl() {
        return playerScriptUrl;
    }

    /**
     * @return The server ABR streaming URL used for SABR playback, or {@code null} if unavailable.
     */
    @Nullable
    public String getServerAbrStreamingUrl() {
        return serverAbrStreamingUrl;
    }

    /**
     * @return The base64 videoPlaybackUstreamerConfig required for SABR requests, or {@code null}.
     */
    @Nullable
    public String getVideoPlaybackUstreamerConfig() {
        return videoPlaybackUstreamerConfig;
    }

    @Nullable
    public String getPoToken() {
        return poToken;
    }

    @Nullable
    public StreamFormat getFormatByItag(int itag) {
        for (StreamFormat format : formats) {
            if (format.getItag() == itag && !format.isSabr()) {
                return format;
            }
        }

        return null;
    }

    @NotNull
    public StreamFormat getBestFormat() {
        StreamFormat bestFormat = null;
        StreamFormat fallbackFormat = null;

        log.debug("Selecting best format. allowAutoDubbedAudio={}", allowAutoDubbedAudio);

        for (StreamFormat format : formats) {
            log.debug("Evaluating format: itag={}, type={}, bitrate={}, isDefault={}, isAutoDubbed={}", 
                     format.getItag(), format.getType().getMimeType(), format.getBitrate(), 
                     format.isDefaultAudioTrack(), format.isAutoDubbed());

            // If we don't allow auto dubbed audio, track the best non-dubbed format as fallback
            if (!allowAutoDubbedAudio && !format.isAutoDubbed()) {
                if (isBetterFormat(format, fallbackFormat)) {
                    fallbackFormat = format;
                }
            }

            if (!allowAutoDubbedAudio && format.isAutoDubbed()) {
                log.debug("Skipping format {} because it is auto-dubbed and allowAutoDubbedAudio is false", format.getItag());
                continue;
            }

            if (!format.isDefaultAudioTrack()) {
                continue;
            }

            if (isBetterFormat(format, bestFormat)) {
                bestFormat = format;
            }
        }

        if (!allowAutoDubbedAudio) {
            boolean isBestVideo = bestFormat != null && bestFormat.getInfo() != null && bestFormat.getInfo().ordinal() >= 3;
            boolean isFallbackAudio = fallbackFormat != null && fallbackFormat.getInfo() != null && fallbackFormat.getInfo().ordinal() < 3;

            if (bestFormat == null || (isBestVideo && isFallbackAudio)) {
                if (fallbackFormat != null) {
                    log.info("Bypassed auto-dubbed default audio track and selected original track (itag={})", fallbackFormat.getItag());
                } else {
                    log.debug("Overriding with non-dubbed fallback, but fallbackFormat is null");
                }
                bestFormat = fallbackFormat;
            }
        }

        if (bestFormat == null) {
            for (StreamFormat format : formats) {
                if (isBetterFormat(format, bestFormat)) {
                    bestFormat = format;
                }
            }
            log.debug("Used general fallback format: {}", bestFormat != null ? bestFormat.getItag() : "null");
        }

        if (bestFormat == null) {
            StringJoiner joiner = new StringJoiner(", ");
            formats.forEach(format -> joiner.add(format.getType().toString()));
            throw new RuntimeException("No supported audio streams available, available types: " + joiner);
        }

        log.debug("Final selected format: itag={}", bestFormat.getItag());
        return bestFormat;
    }

    private static boolean isBetterFormat(StreamFormat format, StreamFormat other) {
        FormatInfo info = format.getInfo();

        if (info == null) {
            return false;
        } else if (other == null) {
            return true;
        } else if (MIME_AUDIO_WEBM.equals(info.mimeType) && format.getAudioChannels() > 2) {
            // Opus with more than 2 audio channels is unsupported by LavaPlayer currently.
            return false;
        } else if (info.ordinal() != other.getInfo().ordinal()) {
            return info.ordinal() < other.getInfo().ordinal();
        } else if (format.isDrc() && !other.isDrc()) {
            // prefer non-drc formats
            // IF ANYTHING BREAKS/SOUNDS BAD, REMOVE THIS
            return false;
        } else {
            return format.getBitrate() > other.getBitrate();
        }
    }
}
