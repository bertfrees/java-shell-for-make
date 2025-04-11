FROM debian:bookworm
ARG TARGETARCH
RUN apt update && apt install build-essential -y
RUN if [ "${TARGETARCH}" = amd64 ]; then \
        apt-get install -y libc6-dev-i386 \
                           mingw-w64; \
    fi

