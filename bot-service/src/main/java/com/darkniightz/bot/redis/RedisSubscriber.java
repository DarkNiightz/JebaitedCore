package com.darkniightz.bot.redis;

@FunctionalInterface
public interface RedisSubscriber {
    void onMessage(String channel, String message);
}
