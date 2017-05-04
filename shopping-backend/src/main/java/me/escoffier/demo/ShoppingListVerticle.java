package me.escoffier.demo;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.redis.RedisClient;
import io.vertx.servicediscovery.ServiceDiscovery;
import io.vertx.servicediscovery.types.RedisDataSource;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by clement on 04/05/2017.
 */
public class ShoppingListVerticle extends AbstractVerticle {

    RedisClient client;
    private final static String KEY = "SHOPPING";

    @Override
    public void start() throws Exception {
        Router router = Router.router(vertx);
        router.get("/").handler(rc -> rc.response().end("Hello"));

        router.get("/shopping").handler(this::getList);
        router.route().handler(BodyHandler.create());
        router.post("/shopping").handler(this::addToList);
        router.delete("/shopping/:name").handler(this::removeFromList);

        ServiceDiscovery.create(vertx, discovery -> {
            RedisDataSource.getRedisClient(discovery,
                svc -> svc.getName().equals("redis"),
                ar -> {
                    if (ar.failed()) {
                        System.out.println("D'oh !");
                    } else {
                        client = ar.result();
                        vertx.createHttpServer()
                            .requestHandler(router::accept)
                            .listen(8080);
                    }
                });
        });


    }

    private void removeFromList(RoutingContext rc) {
        String name = rc.pathParam("name");

        client.hdel(KEY, name, l -> {
            getList(rc);
        });
    }

    private void addToList(RoutingContext rc) {
        JsonObject json = rc.getBodyAsJson();
        String name = json.getString("name");
        Integer quantity = json.getInteger("quantity", 1);

        client.hset(KEY, name, quantity.toString(), l -> {
            getList(rc);
        });
    }

    private void getList(RoutingContext rc) {
        client.hgetall(KEY, json -> {
           if (json.failed()) {
               rc.fail(json.cause());
           } else {
               rc.response()
                   .putHeader("X-Served-By", System.getenv("HOSTNAME"))
                   .end(json.result().encodePrettily());
           }
        });
    }
}
