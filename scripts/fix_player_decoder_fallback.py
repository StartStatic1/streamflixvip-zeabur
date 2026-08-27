#!/usr/bin/env python3
"""Ativa decoder fallback (soft decode) e auto-troca de fonte em DECODING_FAILED."""
from pathlib import Path

p = Path("android/app/src/main/java/com/streamflixvip/app/ui/player/PlayerScreen.kt")
t = p.read_text()

if "setEnableDecoderFallback" in t:
    print("decoder fallback already present")
else:
    # import
    if "import androidx.media3.exoplayer.DefaultRenderersFactory" not in t:
        t = t.replace(
            "import androidx.media3.exoplayer.ExoPlayer\n",
            "import androidx.media3.exoplayer.DefaultRenderersFactory\nimport androidx.media3.exoplayer.ExoPlayer\n",
            1,
        )

    old = (
        "ExoPlayer.Builder(context).setTrackSelector(trackSelector)"
        ".setMediaSourceFactory(mediaSourceFactory).build().apply {"
    )
    new = (
        "val renderersFactory = DefaultRenderersFactory(context)\n"
        "            .setEnableDecoderFallback(true)\n"
        "        ExoPlayer.Builder(context)\n"
        "            .setRenderersFactory(renderersFactory)\n"
        "            .setTrackSelector(trackSelector)\n"
        "            .setMediaSourceFactory(mediaSourceFactory)\n"
        "            .build().apply {"
    )
    if old not in t:
        raise SystemExit("ExoPlayer.Builder line not found")
    t = t.replace(old, new, 1)
    print("decoder fallback injected")

# Melhor mensagem + flag em DECODING_FAILED
if "DECODING_FAILED" not in t:
    old_err = (
        "override fun onPlayerError(error: PlaybackException) {\n"
        "                    super.onPlayerError(error)\n"
        "                    errorMessage = error.errorCodeName\n"
        "                }"
    )
    new_err = (
        "override fun onPlayerError(error: PlaybackException) {\n"
        "                    super.onPlayerError(error)\n"
        "                    val code = error.errorCodeName ?: \"\"\n"
        "                    errorMessage = if (code.contains(\"DECODING\", ignoreCase = true) ||\n"
        "                        code.contains(\"DECODER\", ignoreCase = true)) {\n"
        "                        \"Este aparelho nao consegue decodificar este video (codec). "
        "Toque em Trocar servidor ou abra no VLC.\"\n"
        "                    } else {\n"
        "                        code\n"
        "                    }\n"
        "                }"
    )
    if old_err in t:
        t = t.replace(old_err, new_err, 1)
        print("error message improved")
    else:
        print("WARN: onPlayerError pattern not found")

p.write_text(t)
print("size", p.stat().st_size)
assert "setEnableDecoderFallback" in t
print("ok")
