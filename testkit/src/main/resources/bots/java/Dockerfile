# node:24-bookworm-slim, pinned: see Images.NODE24.
FROM public.ecr.aws/docker/library/node@sha256:0e0ff40c39bc087845bfb27465a0df4ea419520094bc35842ff83dd8cbe6f9b6
WORKDIR /bot
COPY package.json package-lock.json ./
RUN npm ci --omit=dev --no-audit --no-fund
COPY bot.js ./
USER node
CMD ["node", "bot.js"]
