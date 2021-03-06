package org.greencheek.caching.herdcache.memcached;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import net.spy.memcached.ConnectionFactoryBuilder;
import net.spy.memcached.HashAlgorithm;
import org.greencheek.caching.herdcache.CacheWithExpiry;
import org.greencheek.caching.herdcache.RequiresShutdown;
import org.greencheek.caching.herdcache.memcached.config.builder.ElastiCacheCacheConfigBuilder;
import org.greencheek.caching.herdcache.memcached.elasticacheconfig.client.ElastiCacheConfigServerUpdater;
import org.greencheek.caching.herdcache.memcached.elasticacheconfig.client.SimpleVolatileBasedElastiCacheConfigServerUpdater;
import org.greencheek.caching.herdcache.memcached.elasticacheconfig.server.StringServer;
import org.greencheek.caching.herdcache.memcached.spy.extensions.hashing.AsciiXXHashAlogrithm;
import org.greencheek.caching.herdcache.memcached.spy.extensions.hashing.JenkinsHash;
import org.greencheek.caching.herdcache.memcached.spy.extensions.hashing.XXHashAlogrithm;
import org.greencheek.caching.herdcache.memcached.util.MemcachedDaemonFactory;
import org.greencheek.caching.herdcache.memcached.util.MemcachedDaemonWrapper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.Time;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Created by dominictootell on 25/08/2014.
 */
public class TestForceConfigReconnectionElastiCacheMemcachedCaching {


    private MemcachedDaemonWrapper memcached1;
    private MemcachedDaemonWrapper memcached2;

    private ListeningExecutorService executorService;
    private CacheWithExpiry cache;

    @Before
    public void setUp() {
        executorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(10));

        memcached1 = MemcachedDaemonFactory.createMemcachedDaemon(false);
        memcached2 = MemcachedDaemonFactory.createMemcachedDaemon(false);

        if(memcached1.getDaemon()==null) {
            throw new RuntimeException("Unable to start local memcached");
        }

        if(memcached2.getDaemon()==null) {
            throw new RuntimeException("Unable to start local memcached");
        }


    }

    @After
    public void tearDown() {
        if(memcached1!=null) {
            memcached1.getDaemon().stop();
        }

        if(memcached2!=null) {
            memcached2.getDaemon().stop();
        }

        if(cache!=null && cache instanceof RequiresShutdown) {
            ((RequiresShutdown) cache).shutdown();
        }

        executorService.shutdownNow();

    }

    private void testCaching(CacheWithExpiry cache) {
        ListenableFuture<String> val = cache.apply("Key1", () -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return "TO BE STALE CONTENT";
        }, executorService);


        ListenableFuture<String> val2 = cache.apply("Key1", () -> {
            return "B";
        }, executorService);


        ListenableFuture<String> val3 = cache.apply("Key1", () -> {
            return "C";
        }, executorService);

        assertEquals("Value should be key1", "TO BE STALE CONTENT", cache.awaitForFutureOrElse(val, null));
        assertEquals("Value should be key1", "TO BE STALE CONTENT", cache.awaitForFutureOrElse(val2, null));
        assertEquals("Value should be key1", "TO BE STALE CONTENT", cache.awaitForFutureOrElse(val3, null));


        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


        ListenableFuture<String> passThrough = cache.apply("Key1", () -> {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return "New Value";
        }, executorService);


        ListenableFuture<String> val4 = cache.apply("Key1", () -> {
            return "E";
        }, executorService);


        ListenableFuture<String> val5 = cache.apply("Key1", () -> {
            return "F";
        }, executorService);


        assertEquals("Value should be key1", "TO BE STALE CONTENT", cache.awaitForFutureOrElse(val4, null));
        assertEquals("Value should be key1", "TO BE STALE CONTENT", cache.awaitForFutureOrElse(val5, null));

        assertEquals("Value should be key1", "TO BE STALE CONTENT", cache.awaitForFutureOrElse(passThrough, null));


        ListenableFuture<String> val6 = cache.apply("Key1", () -> {
            return "G";
        }, executorService);


        assertEquals("Value should be key1", "TO BE STALE CONTENT", cache.awaitForFutureOrElse(val6, null));


        Map<String,ListenableFuture<String>> cacheWrites = new HashMap<>(200);
        for(int i=0;i<20;i++) {
            final String uuidKey = UUID.randomUUID().toString();
            cacheWrites.put(uuidKey, cache.apply(uuidKey, () -> {
                return uuidKey;
            }, executorService));
        }

        for(Map.Entry<String,ListenableFuture<String>> future : cacheWrites.entrySet()) {
             assertEquals(future.getKey(),cache.awaitForFutureOrElse(future.getValue(),null));
        }

    }

    private void testHashAlgorithm(HashAlgorithm algo) {

        String[] configurationsMessage1 = new String[]{
                "CONFIG cluster 0 147\r\n" + "1\r\n" + "localhost|127.0.0.1|" + memcached1.getPort() + "\r\n" + "\nEND\r\n"
        };

        String[] configurationsMessage2 = new String[]{
                "CONFIG cluster 0 147\r\n" + "1\r\n" + "localhost|127.0.0.1|" + memcached2.getPort() + "\r\n" + "\nEND\r\n"
        };

        StringServer configServer1 = new StringServer(configurationsMessage1, 0, TimeUnit.SECONDS);
        configServer1.before(configurationsMessage1, TimeUnit.SECONDS, -1, false);


        StringServer configServer2 = new StringServer(configurationsMessage2, 0, TimeUnit.SECONDS);
        configServer2.before(configurationsMessage2, TimeUnit.SECONDS, -1, false);

        ElastiCacheConfigServerUpdater configServerUpdater = new SimpleVolatileBasedElastiCacheConfigServerUpdater();

        try {
            cache = new ElastiCacheMemcachedCache<String>(
                    new ElastiCacheCacheConfigBuilder()
                            .setElastiCacheConfigHosts("localhost:" + configServer1.getPort())
                            .setConfigPollingTime(Duration.ofSeconds(10))
                            .setInitialConfigPollingDelay(Duration.ofSeconds(0))
                            .setTimeToLive(Duration.ofSeconds(5))
                            .setProtocol(ConnectionFactoryBuilder.Protocol.TEXT)
                            .setWaitForMemcachedSet(true)
                            .setHashAlgorithm(algo)
                            .setDelayBeforeClientClose(Duration.ofSeconds(1))
                            .setDnsConnectionTimeout(Duration.ofSeconds(2))
                            .setUseStaleCache(true)
                            .setStaleCacheAdditionalTimeToLive(Duration.ofSeconds(4))
                            .setRemoveFutureFromInternalCacheBeforeSettingValue(true)
                            .setConfigUrlUpdater(configServerUpdater)
                            .setReconnectDelay(Duration.ofMillis(100))
                            .buildElastiCacheMemcachedConfig()
            );

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }


            testCaching(cache);
            assertTrue(memcached1.getDaemon().getCache().getCurrentItems()>=1);

            configServerUpdater.connectionUpdated("localhost:" + configServer2.getPort());
            try {
                Thread.sleep(7000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }



            testCaching(cache);

            assertTrue(memcached1.getDaemon().getCache().getCurrentItems()>1);
            assertTrue(memcached2.getDaemon().getCache().getCurrentItems()>1);

        }
        finally {
            configServer1.after();
            configServer2.after();
            if(cache instanceof RequiresShutdown) {
                ((RequiresShutdown)cache).shutdown();
            }
        }

    }

    @Test
    public void testJenkinsHashAlgorithm() {
        testHashAlgorithm(new JenkinsHash());
    }


    @Test
    public void testXXHashAlgorithm() {
        testHashAlgorithm(new XXHashAlogrithm());
    }

    @Test
    public void testAsciiXXHashAlgorithm() {
        testHashAlgorithm(new AsciiXXHashAlogrithm());
    }


}
