FROM itzg/minecraft-server:latest

ENV EULA=TRUE \
    TYPE=PAPER \
    VERSION=LATEST \
    MEMORY=4G

COPY --chown=1000:1000 target/JebaitedCore.jar /plugins/JebaitedCore.jar
