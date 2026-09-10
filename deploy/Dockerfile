# 데모 배포본 이미지 — jar 하나만 담는다.
#
# 🔴 빌드 컨텍스트는 «저장소 루트가 아니라 deploy/» 다.
#    docker build 는 git 이 아니라 «디스크» 를 본다. 루트를 컨텍스트로 잡으면
#    gitignore 여부와 무관하게 docs/samples/(공급사 단가표 원본 93MB)·data/·
#    uploads/ 가 통째로 빌더에 업로드된다.  → docs/design-deploy-tooling.md §7-1
#
#   빌드:  ./mvnw -Pfrontend clean package
#          cp target/esti-*.jar deploy/esti.jar
#          docker build -t esti-demo:local deploy
#   실행:  docker run --rm -p 8080:8080 -m 1g esti-demo:local
#
# 베이스는 glibc(jammy). Alpine(musl)을 쓰지 않는 이유는 VectorImageConverter 가
# AWT/ImageIO 를 쓰기 때문이다 — 아끼는 건 90MB 뿐인데 위험은 런타임에 나타난다.

FROM eclipse-temurin:17-jre-jammy

# 루트로 돌리지 않는다.
RUN useradd --system --create-home --uid 10001 esti

# 🔴 uploads 를 «미리, esti 소유로» 만들어 둔다.
#    WORKDIR 이 만드는 디렉터리는 root 소유라, 이게 없으면 앱이 uid 10001 로
#    ./uploads/product-images 를 못 만들고 DemoSeedRunner 가 WARN 만 남긴 채
#    «제품 이미지가 한 장도 없는» 데모가 뜬다 (AccessDeniedException: /app/uploads).
RUN install -d -o esti -g esti /app /app/uploads

WORKDIR /app
COPY --chown=esti:esti esti.jar /app/esti.jar
USER esti

# 🔴 프로파일을 빠뜨리면 local 이 뜬다. local 은 server.address=127.0.0.1 이라
#    컨테이너 밖에서 «영원히» 붙지 못하고 헬스체크가 끝없이 실패한다.  → §7-4
ENV SPRING_PROFILES_ACTIVE=demo

# 컨테이너 메모리 한도를 JVM 에 알린다. 안 주면 호스트 기준으로 힙을 잡는다. → §7-5
# ExitOnOutOfMemoryError: 죽은 채 살아있는 것보다 죽고 재시작되는 편이 낫다.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/esti.jar"]
