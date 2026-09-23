# node:24-bookworm-slim, pinned: see Images.NODE24.
FROM public.ecr.aws/docker/library/node@sha256:0e0ff40c39bc087845bfb27465a0df4ea419520094bc35842ff83dd8cbe6f9b6
# raknet-native ships no build for this Node version, so it is compiled here. The pure JavaScript
# RakNet (jsp-raknet) cannot even discover a Geyser server.
RUN apt-get update && apt-get install -y --no-install-recommends cmake make g++ python3 ca-certificates \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /bot
COPY package.json package-lock.json ./
RUN npm ci --omit=dev --ignore-scripts --no-audit --no-fund \
    && cd node_modules/raknet-native && npx --no-install cmake-js compile
COPY bot.js ./
USER node
CMD ["node", "bot.js"]
