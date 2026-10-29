set -e
cd /home/user/DHUN/production/what-if-brain-100x
FF=/home/user/.venv/lib/python3.11/site-packages/imageio_ffmpeg/binaries/ffmpeg-linux-x86_64-v7.0.2
printf "file 'seg_01.mp4'\nfile 'seg_02.mp4'\nfile 'seg_03.mp4'\nfile 'seg_04.mp4'\nfile 'seg_05.mp4'\nfile 'seg_06.mp4'\n" > vlist.txt
$FF -y -loglevel error -f concat -safe 0 -i vlist.txt -c copy video_silent.mp4
$FF -y -loglevel error -i video_silent.mp4 -i audio/narration.wav -i audio/ambience.wav \
  -filter_complex "[2:a]volume=0.55[amb];[1:a][amb]amix=inputs=2:duration=first:weights=1 0.5[a]" \
  -map 0:v -map "[a]" -c:v copy -c:a aac -b:a 192k -ar 48000 -shortest -movflags +faststart \
  "What_If_Your_Brain_Ran_100x_Faster.mp4"
$FF -i "What_If_Your_Brain_Ran_100x_Faster.mp4" 2>&1 | grep -E "Duration|Stream"
