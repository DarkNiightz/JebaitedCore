package com.darkniightz.bot.redis;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.pubsub.api.async.RedisPubSubAsyncCommands;
import io.lettuce.core.pubsub.api.sync.RedisPubSubCommands;

public final class RedisBus {
    private final RedisClient client;
    private final StatefulRedisConnection<String, String> commandConnection;
    private final StatefulRedisPubSubConnection<String, String> pubSubConnection;

    public RedisBus(String redisUri) {
        this.client = RedisClient.create(redisUri);
        this.commandConnection = client.connect();
        this.pubSubConnection = client.connectPubSub();
    }

    public void publish(String channel, String payload) {
        RedisPubSubAsyncCommands<String, String> async = pubSubConnection.async();
        async.publish(channel, payload);
    }

    public void subscribe(String channel, RedisSubscriber subscriber) {
        pubSubConnection.addListener(new RedisPubSubAdapter<>() {
            @Override
            public void message(String ch, String message) {
                subscriber.onMessage(ch, message);
            }
        });
        RedisPubSubCommands<String, String> sync = pubSubConnection.sync();
        sync.subscribe(channel);
    }

    public boolean ping() {
        String pong = commandConnection.sync().ping();
        return "PONG".equalsIgnoreCase(pong);
    }

    public void close() {
        pubSubConnection.close();
        commandConnection.close();
        client.shutdown();
    }
}
