FROM node:22-alpine AS runtime

WORKDIR /opt/aegis/product
ENV NODE_ENV=production
ENV PORT=8080

COPY package.json ./
COPY apps ./apps
COPY packages ./packages

RUN npm install --omit=dev --ignore-scripts --no-audit --no-fund \
    && npm cache clean --force

USER node
EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=3 \
  CMD node -e "fetch('http://127.0.0.1:8080/health/ready').then(r=>{if(!r.ok)process.exit(1)}).catch(()=>process.exit(1))"

CMD ["node", "apps/api-server/index.js"]
