package org.greencheek.caching.herdcache.memcached;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import net.spy.memcached.ConnectionFactoryBuilder;
import net.spy.memcached.HashAlgorithm;
import org.greencheek.caching.herdcache.CacheWithExpiry;
import org.greencheek.caching.herdcache.RequiresShutdown;
import org.greencheek.caching.herdcache.memcached.config.builder.ElastiCacheCacheConfigBuilder;
import org.greencheek.caching.herdcache.memcached.elasticacheconfig.client.ClientClusterUpdateObserver;
import org.greencheek.caching.herdcache.memcached.elasticacheconfig.server.StringServer;
import org.greencheek.caching.herdcache.memcached.spy.extensions.hashing.AsciiXXHashAlogrithm;
import org.greencheek.caching.herdcache.memcached.spy.extensions.hashing.JenkinsHash;
import org.greencheek.caching.herdcache.memcached.spy.extensions.hashing.XXHashAlogrithm;
import org.greencheek.caching.herdcache.memcached.util.MemcachedDaemonFactory;
import org.greencheek.caching.herdcache.memcached.util.MemcachedDaemonWrapper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Created by dominictootell on 25/08/2014.
 */
public class TestMultipleHostsWithNonCachingConfigElastiCacheMemcachedCaching {

    MemcachedDaemonWrapper memcached1;
    MemcachedDaemonWrapper memcached2;

    ListeningExecutorService executorService;
    CacheWithExpiry cache;

    @Before
    public void setUp() {
        executorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(5));

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

    CacheWithExpiry<String> createCache(int configServerPort,HashAlgorithm algo,ClientClusterUpdateObserver observer) {
        return new ElastiCacheMemcachedCache<String>(
                new ElastiCacheCacheConfigBuilder()
                        .setElastiCacheConfigHosts("localhost:" + configServerPort)
                        .setConfigPollingTime(Duration.ofSeconds(9))
                        .setInitialConfigPollingDelay(Duration.ofSeconds(0))
                        .setTimeToLive(Duration.ofSeconds(2))
                        .setProtocol(ConnectionFactoryBuilder.Protocol.TEXT)
                        .setWaitForMemcachedSet(true)
                        .setSetWaitDuration(Duration.ofSeconds(10))
                        .setHashAlgorithm(algo)
                        .setDelayBeforeClientClose(Duration.ofSeconds(1))
                        .setDnsConnectionTimeout(Duration.ofSeconds(2))
                        .setUseStaleCache(true)
                        .setStaleCacheAdditionalTimeToLive(Duration.ofSeconds(4))
                        .setRemoveFutureFromInternalCacheBeforeSettingValue(true)
                        .addElastiCacheClientClusterUpdateObserver(observer)
                        .buildElastiCacheMemcachedConfig()
        );
    }

    private void testStaleCaching(CacheWithExpiry cache) {
        ListenableFuture<String> val = cache.apply("Key1", () -> {
            try {
                Thread.sleep(500);
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
                Thread.sleep(500);
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

        assertEquals("Value should be key1", "New Value", cache.awaitForFutureOrElse(passThrough, null));


        ListenableFuture<String> val6 = cache.apply("Key1", () -> {
            return "G";
        }, executorService);


        assertEquals("Value should be key1", "New Value", cache.awaitForFutureOrElse(val6, null));


        Map<String,ListenableFuture<String>> cacheWrites = new HashMap<>(100);
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

    private boolean waitOnInt(AtomicInteger integer) {
        boolean ok = true;
        long start = System.currentTimeMillis();
        while(integer.get()>0) {
            long now = System.currentTimeMillis();
            if((now-start) > 20000) {
                ok = false;
                break;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        integer.set(1);
        return ok;
    }

    private void testHashAlgorithm(HashAlgorithm algo) {

        String[] configurationsMessage = new String[]{
                "CONFIG cluster 0 147\r\n" + "1\r\n" + "localhost|127.0.0.1|" + memcached1.getPort() + "\r\n" + "\nEND\r\n",
                "CONFIG cluster 0 147\r\n" + "2\r\n" + "localhost|127.0.0.1|" + memcached1.getPort() + " localhost|127.0.0.1|" + memcached2.getPort() + "\r\n" + "\nEND\r\n",
                "CONFIG cluster 0 147\r\n" + "2\r\n" + "localhost|127.0.0.1|" + memcached1.getPort() + " localhost|127.0.0.1|" + memcached2.getPort() + "\r\n" + "\nEND\r\n",
                "CONFIG cluster 0 147\r\n" + "2\r\n" + "localhost|127.0.0.1|" + memcached1.getPort() + " localhost|127.0.0.1|" + memcached2.getPort() + "\r\n" + "\nEND\r\n",
                "CONFIG cluster 0 147\r\n" + "4\r\n" + "localhost|127.0.0.1|" + memcached1.getPort() + "\r\n" + "\nEND\r\n",

        };

        StringServer server = new StringServer(configurationsMessage, 0, TimeUnit.SECONDS);

        try {
            server.before(configurationsMessage, TimeUnit.SECONDS, -1, false);

            final AtomicInteger latch = new AtomicInteger(1);

            ClientClusterUpdateObserver observer = (updated) -> {
                latch.decrementAndGet();
            };

            cache = createCache(server.getPort(),algo,observer);


            assertTrue(waitOnInt(latch));

            testStaleCaching(cache);
            assertTrue(memcached1.getDaemon().getCache().getCurrentItems() >= 1);


            if(cache instanceof ClearableCache) {
                ((ClearableCache)cache).clear(true);
                memcached1.getDaemon().getCache().flush_all();
                memcached2.getDaemon().getCache().flush_all();
            }

            assertTrue(waitOnInt(latch));

            testStaleCaching(cache);

            assertTrue(memcached1.getDaemon().getCache().getCurrentItems()>1);
            assertTrue(memcached2.getDaemon().getCache().getCurrentItems()>1);

            if(cache instanceof ClearableCache) {
                ((ClearableCache)cache).clear(true);
                memcached1.getDaemon().getCache().flush_all();
                memcached2.getDaemon().getCache().flush_all();
            }

            assertTrue(waitOnInt(latch));

            testStaleCaching(cache);

            assertTrue(memcached1.getDaemon().getCache().getCurrentItems()>1);
            assertTrue(memcached2.getDaemon().getCache().getCurrentItems()>1);

            if(cache instanceof ClearableCache) {
                ((ClearableCache)cache).clear(true);
                memcached1.getDaemon().getCache().flush_all();
                memcached2.getDaemon().getCache().flush_all();
            }

            assertTrue(waitOnInt(latch));

            testStaleCaching(cache);

            assertTrue(memcached1.getDaemon().getCache().getCurrentItems()>1);
            assertTrue(memcached2.getDaemon().getCache().getCurrentItems()>1);

            if(cache instanceof ClearableCache) {
                ((ClearableCache)cache).clear(true);
                memcached1.getDaemon().getCache().flush_all();
                memcached2.getDaemon().getCache().flush_all();
            }


            assertTrue(waitOnInt(latch));

            testStaleCaching(cache);

            assertTrue(memcached1.getDaemon().getCache().getCurrentItems()>1);
            assertTrue(memcached2.getDaemon().getCache().getCurrentItems()==0);

        }
        finally {
            server.after();
            if(cache instanceof RequiresShutdown) {
                ((RequiresShutdown)cache).shutdown();
            }
        }

    }


    private void clearCache(MemcachedDaemonWrapper wrapper) {
        if(getItems(wrapper)>0) {
            wrapper.getDaemon().getCache().flush_all();
        }
    }

    private long getItems(MemcachedDaemonWrapper wrapper) {
        return wrapper.getDaemon().getCache().getCurrentItems();
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
