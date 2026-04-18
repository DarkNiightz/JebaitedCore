# Legacy / optional — NOT used by JebaitedNetwork/docker-compose.yml.
# The live stack runs itzg/minecraft-server from Docker Hub with MC Server/ bind-mounted.
# Keep only for ad-hoc experiments or a custom image build; see docs/DOCKER.md.
FROM itzg/minecraft-server:latest

ENV EULA=TRUE \
    TYPE=PAPER \
    VERSION=LATEST \
    MEMORY=4G

COPY --chown=1000:1000 target/JebaitedCore.jar /plugins/JebaitedCore.jar
