# What If Your Brain Ran 100x Faster? — production package

Everything in this folder was built in this workspace. Artlist was **not** connected during
this session, so the Seedance 2.5 route was replaced by a fully local render — same lock:
1080p, 16:9, native-audio-style narration, minimalist black stick figures.

## Deliverables

| File | What it is |
|---|---|
| `What_If_Your_Brain_Ran_100x_Faster.mp4` | Final film — 3:00, 1920x1080, 30fps, H.264 + AAC stereo |
| `seg_01.mp4` … `seg_06.mp4` | The six 30-second segments, in order |
| `thumbnail_100x.jpg` | YouTube thumbnail, 1280x720 |
| `SCRIPT.md` | Approved script, scene table, character bible |
| `YOUTUBE_PACKAGE.md` | Title, description, hashtags, keywords, thumbnail copy, pinned comment |
| `core.py`, `scenes.py`, `render.py` | The renderer (Pillow vector draw → ffmpeg pipe) |
| `thumb.py` | Thumbnail generator |
| `audio/` | Narration takes (mp3), tempo-fitted 30s WAVs, ambience bed, final narration mix |

## Rebuild

```bash
python3 -m venv .venv && .venv/bin/pip install pillow numpy imageio-ffmpeg
.venv/bin/python render.py --preview 42      # single frame
bash build.sh                                # all six segments (parallel, ~8 min)
bash assemble.sh                             # concat + audio + master
.venv/bin/python thumb.py
```

## Style lock

- Stick figures: circular black heads, white oval eyes with black pupils, 15px limbs at s=1.0.
- Kai: blue wristband, ~258px tall at s=1.0, somber until the final scene.
- Min: red hair tie, 0.9 scale, swinging feet.
- Palette: cream #F5F0E6 ground, muted blue #3A6EA5, muted red #B5483D, navy night scenes.
- Camera: wide → medium → eye close-ups, slow pushes, whip pans on action, no shake-cam.
- Narration: one adult male narrator, warm, medium-low, documentary delivery.

## Narration timing

Scene cuts are aligned to measured silence boundaries in each narration segment, so each
visual beat lands on the line it illustrates. Segments 1–4 end ~4–7s before the 30s mark;
that gap is intentional breathing room before the next cut.
