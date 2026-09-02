# 跑docker并挂起
docker run -dit --name restored-session-test -w /workspace agentscope-dev:ubuntu24.04 sh -c 'while :; do sleep 3600; done'


# 将快照放进去
docker exec restored-session1 mkdir -p /workspace
docker exec -i restored-session1 tar -xf - -C /workspace < .agentscope/sandbox-snapshots/star/session1/<snapshot-id>.tar
