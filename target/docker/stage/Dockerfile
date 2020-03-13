FROM java:openjdk-8 as stage0
LABEL snp-multi-stage="intermediate"
LABEL snp-multi-stage-id="f6dccc2f-4792-40ba-8226-c24b778f106a"
WORKDIR /opt/docker
COPY opt /opt
USER root
RUN ["chmod", "-R", "u=rX,g=rX", "/opt/docker"]
RUN ["chmod", "u+x,g+x", "/opt/docker/bin/akka-sample-cluster-scala"]

FROM java:openjdk-8
LABEL MAINTAINER="tpaesen"
USER root
RUN id -u demiourgos728 1>/dev/null 2>&1 || (( getent group 0 1>/dev/null 2>&1 || ( type groupadd 1>/dev/null 2>&1 && groupadd -g 0 root || addgroup -g 0 -S root )) && ( type useradd 1>/dev/null 2>&1 && useradd --system --create-home --uid 1001 --gid 0 demiourgos728 || adduser -S -u 1001 -G root demiourgos728 ))
WORKDIR /opt/docker
COPY --from=stage0 --chown=demiourgos728:root /opt/docker /opt/docker
EXPOSE 9000 8558 2552
USER 1001:0
ENTRYPOINT ["/opt/docker/bin/akka-sample-cluster-scala"]
CMD []
