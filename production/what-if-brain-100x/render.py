"""Entry point: renders "What If Your Brain Ran 100x Faster?" segments."""
import argparse
import imageio_ffmpeg
from core import W, H, FPS, SEG, NSEG, Frame, clamp
import scenes

SCENE_TABLES = {
    0: [(4.6, scenes.s1_bedroom), (2.4, scenes.s1_clock), (4.0, scenes.s1_friend),
        (19.0, scenes.s1_eyes)],
    1: [(6.8, scenes.s2_exam), (6.8, scenes.s2_chess), (5.9, scenes.s2_board),
        (10.5, scenes.s2_hero)],
    2: [(8.6, scenes.s3_street), (2.9, scenes.s3_macro), (6.5, scenes.s3_sign),
        (6.0, scenes.s3_crowd), (6.0, scenes.s3_crowd_close)],
    3: [(9.6, scenes.s4_dinner), (7.4, scenes.s4_hall), (13.0, scenes.s4_alone)],
    4: [(9.5, scenes.s5_night), (8.2, scenes.s5_wait), (12.3, scenes.s5_choose)],
    5: [(17.5, scenes.s6_bench), (8.0, scenes.s6_roof), (4.5, scenes.s6_kitchen)],
}


def frame_at(gt):
    seg = min(int(gt // SEG), NSEG - 1)
    local = gt - seg * SEG
    acc = 0.0
    table = SCENE_TABLES[seg]
    for i, (dur, fn) in enumerate(table):
        if local < acc + dur or i == len(table) - 1:
            u = clamp((local - acc) / dur)
            f = Frame()
            fn(f, u, gt, local - acc)
            return f.finish()
        acc += dur
    return Frame().finish()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview", type=float, default=None)
    ap.add_argument("--out", default=None)
    ap.add_argument("--video", default=None)
    ap.add_argument("--start", type=float, default=0.0)
    ap.add_argument("--end", type=float, default=float(NSEG * SEG))
    ap.add_argument("--fps", type=int, default=FPS)
    args = ap.parse_args()

    if args.preview is not None:
        img = frame_at(args.preview)
        out = args.out or f"/tmp/preview_{args.preview:05.1f}.png"
        img.save(out)
        print("saved", out)
        return

    out = args.video or "video_silent.mp4"
    n0, n1 = int(args.start * args.fps), int(args.end * args.fps)
    ff = imageio_ffmpeg.write_frames(
        out, (W, H), fps=args.fps, codec="libx264", pix_fmt_in="rgb24", pix_fmt_out="yuv420p",
        output_params=["-crf", "20", "-preset", "medium", "-movflags", "+faststart", "-g", "60"],
        macro_block_size=1,
    )
    ff.send(None)
    for n in range(n0, n1):
        ff.send(frame_at(n / args.fps).tobytes())
        if n % 120 == 0:
            print(f"frame {n}/{n1}  t={n/args.fps:.1f}s", flush=True)
    ff.close()
    print("done", out)


if __name__ == "__main__":
    main()
