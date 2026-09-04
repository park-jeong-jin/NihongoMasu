#!/bin/sh
# 예문을 형태소로 끊어 app/src/main/resources/tokens.tsv 를 다시 만든다.
# vocab.tsv 의 예문을 고쳤을 때만 돌리면 된다.
#
# 형태소 분석기는 여기서만 쓰는 빌드 도구다 — APK에는 안 들어간다.
# gradle 캐시에 있으면 그것을 쓰고, 없으면 메이븐에서 받아 tools/.cache 에 둔다.
set -e
cd "$(dirname "$0")/.."

JDK=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
V=0.9.0
CACHE=tools/.cache
mkdir -p "$CACHE"

for a in kuromoji-ipadic kuromoji-core; do
  [ -f "$CACHE/$a-$V.jar" ] && continue
  found=$(find "$HOME/.gradle/caches" -name "$a-$V.jar" 2>/dev/null | head -1)
  if [ -n "$found" ]; then
    cp "$found" "$CACHE/$a-$V.jar"
  else
    echo "받는 중: $a-$V.jar"
    curl -sSfL -o "$CACHE/$a-$V.jar" \
      "https://repo1.maven.org/maven2/com/atilika/kuromoji/$a/$V/$a-$V.jar"
  fi
done

"$JDK/bin/java" -cp "$CACHE/kuromoji-ipadic-$V.jar:$CACHE/kuromoji-core-$V.jar" \
  tools/Tok.java app/src/main/resources/vocab.tsv app/src/main/resources/tokens.tsv
