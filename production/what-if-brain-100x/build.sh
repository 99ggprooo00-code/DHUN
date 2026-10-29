set -e
cd /home/user/DHUN/production/what-if-brain-100x
PY=/home/user/.venv/bin/python
render() {  # $1=index  $2=start  $3=end
  $PY render.py --video seg_$1.mp4 --start $2 --end $3
}
render 01 0 30  &
render 02 30 60 &
wait
render 03 60 90 &
render 04 90 120 &
wait
render 05 120 150 &
render 06 150 180 &
wait
echo "ALL SEGMENTS RENDERED"
