set -e
# Rebuild narration.wav + ambience.wav from the narration takes.
cd /home/user/DHUN/production/what-if-brain-100x
FF=/home/user/.venv/lib/python3.11/site-packages/imageio_ffmpeg/binaries/ffmpeg-linux-x86_64-v7.0.2
cd audio
for i in 01 02 03 04 05 06; do
  d=$($FF -i seg$i.mp3 2>&1 | grep -o 'Duration: [0-9:.]*' | head -1 | cut -d' ' -f2)
  secs=$(echo $d | awk -F: '{print $1*3600+$2*60+$3}')
  tempo=$(/home/user/.venv/bin/python -c "print(f'{max(1.0, $secs/29.2):.5f}')")
  $FF -y -loglevel error -i seg$i.mp3 -filter:a "atempo=$tempo,apad,atrim=0:30,afade=t=in:st=0:d=0.12" \
      -ar 48000 -ac 2 fit$i.wav
done
printf "file 'fit01.wav'\nfile 'fit02.wav'\nfile 'fit03.wav'\nfile 'fit04.wav'\nfile 'fit05.wav'\nfile 'fit06.wav'\n" > list.txt
$FF -y -loglevel error -f concat -safe 0 -i list.txt -af "loudnorm=I=-16:TP=-1.5:LRA=11" -ar 48000 -ac 2 narration.wav
cd ..
/home/user/.venv/bin/python gen_ambience.py
