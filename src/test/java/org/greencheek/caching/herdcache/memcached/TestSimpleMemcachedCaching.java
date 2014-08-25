package org.greencheek.caching.herdcache.memcached;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import net.spy.memcached.ConnectionFactoryBuilder;
import net.spy.memcached.HashAlgorithm;
import org.greencheek.caching.herdcache.CacheWithExpiry;
import org.greencheek.caching.herdcache.RequiresShutdown;
import org.greencheek.caching.herdcache.memcached.config.builder.ElastiCacheCacheConfigBuilder;
import org.greencheek.caching.herdcache.memcached.config.builder.MemcachedCacheConfigBuilder;
import org.greencheek.caching.herdcache.memcached.keyhashing.KeyHashingType;
import org.greencheek.caching.herdcache.memcached.spy.extensions.BaseSerializingTranscoder;
import org.greencheek.caching.herdcache.memcached.spy.extensions.FastSerializingTranscoder;
import org.greencheek.caching.herdcache.memcached.spy.extensions.SerializingTranscoder;
import org.greencheek.caching.herdcache.memcached.spy.extensions.hashing.AsciiXXHashAlogrithm;
import org.greencheek.caching.herdcache.memcached.spy.extensions.hashing.JenkinsHash;
import org.greencheek.caching.herdcache.memcached.spy.extensions.hashing.XXHashAlogrithm;
import org.greencheek.caching.herdcache.memcached.util.MemcachedDaemonFactory;
import org.greencheek.caching.herdcache.memcached.util.MemcachedDaemonWrapper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.Serializable;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executors;

import static org.junit.Assert.assertEquals;

/**
 * Created by dominictootell on 25/08/2014.
 */
public class TestSimpleMemcachedCaching {
    private String largeCacheValue = "# Memcached For Spray #\n" +
            "\n" +
            "This library is an extension of the spray-caching (http://spray.io/documentation/1.2.1/spray-caching/).  It provides\n" +
            "a memcached backed cache for spray caching.\n" +
            "\n" +
            "## Overview ##\n" +
            "\n" +
            "Uses an internal ConcurrentLinkedHashMap (https://code.google.com/p/concurrentlinkedhashmap/) to provide a storage of\n" +
            "keys to executing futures.\n" +
            "\n" +
            "When the future completes the computed value (which must be a serializable object), is asynchronously stored in memcached.\n" +
            "At this point the entry is removed from the internal cache; and will only exist in memcached.\n" +
            "\n" +
            "Before the value of the future is computed, memcached is checked for a value.  If a pre-existing value is found this is\n" +
            "returned.\n" +
            "\n" +
            "The keys for the cache must have a toString method that represents that object.  Memcached requires string keys, and serialized\n" +
            "objects.\n" +
            "\n" +
            "\n" +
            "## Dependencies ##\n" +
            "\n" +
            "The library uses the Java Spy Memcached library (https://code.google.com/p/spymemcached/), to communicate with memcached.\n" +
            "\n" +
            "## Thundering Herd ##\n" +
            "\n" +
            "The spray caching api, isn't quite suited for distributed caching implementations.  The existing LRU (Simple and Expiring),\n" +
            "store in the cache a future.  As a result you can't really store a future in a distributed cache.  As the future may or may\n" +
            "not be completed, and is specific to the client that generated the future.\n" +
            "\n" +
            "This architecture lend it's self nicely to the thundering herd issue (as detailed on http://spray.io/documentation/1.2.1/spray-caching/):\n" +
            "\n" +
            "    his approach has the advantage of nicely taking care of the thundering herds problem where many requests to a\n" +
            "    particular cache key (e.g. a resource URI) arrive before the first one could be completed. Normally\n" +
            "    (without special guarding techniques, like so-called “cowboy entries) this can cause many requests\n" +
            "    to compete for system resources while trying to compute the same result thereby greatly reducing overall\n" +
            "    system performance. When you use a spray-caching cache the very first request that arrives for a certain\n" +
            "    cache key causes a future to be put into the cache which all later requests then “hook into. As soon as\n" +
            "    the first request completes all other ones complete as well. This minimizes processing time and server\n" +
            "    load for all requests.\n" +
            "\n" +
            "Basically if many requests come in for the same key, and the value has not yet been computed.  Rather than each\n" +
            "request compute the value, all the requests wait on the 1 invocation of the value to be computed.\n" +
            "\n" +
            "### Memcached thundering herd, how does it do it  ###\n" +
            "\n" +
            "There is really no real difference between the SimpleLruCache (https://github.com/spray/spray/blob/v1.2.1/spray-caching/src/main/scala/spray/caching/LruCache.scala#L54)\n" +
            "and the memcached library implementation.\n" +
            "\n" +
            "\n" +
            "# Memcached For Spray #\n" +
            "\n" +
            "This library is an extension of the spray-caching (http://spray.io/documentation/1.2.1/spray-caching/).  It provides\n" +
            "a memcached backed cache for spray caching.\n" +
            "\n" +
            "## Overview ##\n" +
            "\n" +
            "Uses an internal ConcurrentLinkedHashMap (https://code.google.com/p/concurrentlinkedhashmap/) to provide a storage of\n" +
            "keys to executing futures.\n" +
            "\n" +
            "When the future completes the computed value (which must be a serializable object), is asynchronously stored in memcached.\n" +
            "At this point the entry is removed from the internal cache; and will only exist in memcached.\n" +
            "\n" +
            "Before the value of the future is computed, memcached is checked for a value.  If a pre-existing value is found this is\n" +
            "returned.\n" +
            "\n" +
            "The keys for the cache must have a toString method that represents that object.  Memcached requires string keys, and serialized\n" +
            "objects.\n" +
            "\n" +
            "\n" +
            "## Dependencies ##\n" +
            "\n" +
            "The library uses the Java Spy Memcached library (https://code.google.com/p/spymemcached/), to communicate with memcached.\n" +
            "\n" +
            "## Thundering Herd ##\n" +
            "\n" +
            "The spray caching api, isn't quite suited for distributed caching implementations.  The existing LRU (Simple and Expiring),\n" +
            "store in the cache a future.  As a result you can't really store a future in a distributed cache.  As the future may or may\n" +
            "not be completed, and is specific to the client that generated the future.\n" +
            "\n" +
            "This architecture lend it's self nicely to the thundering herd issue (as detailed on http://spray.io/documentation/1.2.1/spray-caching/):\n" +
            "\n" +
            "    his approach has the advantage of nicely taking care of the thundering herds problem where many requests to a\n" +
            "    particular cache key (e.g. a resource URI) arrive before the first one could be completed. Normally\n" +
            "    (without special guarding techniques, like so-called “cowboy entries) this can cause many requests\n" +
            "    to compete for system resources while trying to compute the same result thereby greatly reducing overall\n" +
            "    system performance. When you use a spray-caching cache the very first request that arrives for a certain\n" +
            "    cache key causes a future to be put into the cache which all later requests then “hook into. As soon as\n" +
            "    the first request completes all other ones complete as well. This minimizes processing time and server\n" +
            "    load for all requests.\n" +
            "\n" +
            "Basically if many requests come in for the same key, and the value has not yet been computed.  Rather than each\n" +
            "request compute the value, all the requests wait on the 1 invocation of the value to be computed.\n" +
            "\n" +
            "### Memcached thundering herd, how does it do it  ###\n" +
            "\n" +
            "There is really no real difference between the SimpleLruCache (https://github.com/spray/spray/blob/v1.2.1/spray-caching/src/main/scala/spray/caching/LruCache.scala#L54)\n" +
            "and the memcached library implementation.\n" +
            "\n" +
            "\n" +
            "# Memcached For Spray #\n" +
            "\n" +
            "This library is an extension of the spray-caching (http://spray.io/documentation/1.2.1/spray-caching/).  It provides\n" +
            "a memcached backed cache for spray caching.\n" +
            "\n" +
            "## Overview ##\n" +
            "\n" +
            "Uses an internal ConcurrentLinkedHashMap (https://code.google.com/p/concurrentlinkedhashmap/) to provide a storage of\n" +
            "keys to executing futures.\n" +
            "\n" +
            "When the future completes the computed value (which must be a serializable object), is asynchronously stored in memcached.\n" +
            "At this point the entry is removed from the internal cache; and will only exist in memcached.\n" +
            "\n" +
            "Before the value of the future is computed, memcached is checked for a value.  If a pre-existing value is found this is\n" +
            "returned.\n" +
            "\n" +
            "The keys for the cache must have a toString method that represents that object.  Memcached requires string keys, and serialized\n" +
            "objects.\n" +
            "\n" +
            "\n" +
            "## Dependencies ##\n" +
            "\n" +
            "The library uses the Java Spy Memcached library (https://code.google.com/p/spymemcached/), to communicate with memcached.\n" +
            "\n" +
            "## Thundering Herd ##\n" +
            "\n" +
            "The spray caching api, isn't quite suited for distributed caching implementations.  The existing LRU (Simple and Expiring),\n" +
            "store in the cache a future.  As a result you can't really store a future in a distributed cache.  As the future may or may\n" +
            "not be completed, and is specific to the client that generated the future.\n" +
            "\n" +
            "This architecture lend it's self nicely to the thundering herd issue (as detailed on http://spray.io/documentation/1.2.1/spray-caching/):\n" +
            "\n" +
            "    his approach has the advantage of nicely taking care of the thundering herds problem where many requests to a\n" +
            "    particular cache key (e.g. a resource URI) arrive before the first one could be completed. Normally\n" +
            "    (without special guarding techniques, like so-called “cowboy entries) this can cause many requests\n" +
            "    to compete for system resources while trying to compute the same result thereby greatly reducing overall\n" +
            "    system performance. When you use a spray-caching cache the very first request that arrives for a certain\n" +
            "    cache key causes a future to be put into the cache which all later requests then “hook into. As soon as\n" +
            "    the first request completes all other ones complete as well. This minimizes processing time and server\n" +
            "    load for all requests.\n" +
            "\n" +
            "Basically if many requests come in for the same key, and the value has not yet been computed.  Rather than each\n" +
            "request compute the value, all the requests wait on the 1 invocation of the value to be computed.\n" +
            "\n" +
            "### Memcached thundering herd, how does it do it  ###\n" +
            "\n" +
            "There is really no real difference between the SimpleLruCache (https://github.com/spray/spray/blob/v1.2.1/spray-caching/src/main/scala/spray/caching/LruCache.scala#L54)\n" +
            "and the memcached library implementation.\n" +
            "\n" +
            "\n" +
            "# Memcached For Spray #\n" +
            "\n" +
            "This library is an extension of the spray-caching (http://spray.io/documentation/1.2.1/spray-caching/).  It provides\n" +
            "a memcached backed cache for spray caching.\n" +
            "\n" +
            "## Overview ##\n" +
            "\n" +
            "Uses an internal ConcurrentLinkedHashMap (https://code.google.com/p/concurrentlinkedhashmap/) to provide a storage of\n" +
            "keys to executing futures.\n" +
            "\n" +
            "When the future completes the computed value (which must be a serializable object), is asynchronously stored in memcached.\n" +
            "At this point the entry is removed from the internal cache; and will only exist in memcached.\n" +
            "\n" +
            "Before the value of the future is computed, memcached is checked for a value.  If a pre-existing value is found this is\n" +
            "returned.\n" +
            "\n" +
            "The keys for the cache must have a toString method that represents that object.  Memcached requires string keys, and serialized\n" +
            "objects.\n" +
            "\n" +
            "\n" +
            "## Dependencies ##\n" +
            "\n" +
            "The library uses the Java Spy Memcached library (https://code.google.com/p/spymemcached/), to communicate with memcached.\n" +
            "\n" +
            "## Thundering Herd ##\n" +
            "\n" +
            "The spray caching api, isn't quite suited for distributed caching implementations.  The existing LRU (Simple and Expiring),\n" +
            "store in the cache a future.  As a result you can't really store a future in a distributed cache.  As the future may or may\n" +
            "not be completed, and is specific to the client that generated the future.\n" +
            "\n" +
            "This architecture lend it's self nicely to the thundering herd issue (as detailed on http://spray.io/documentation/1.2.1/spray-caching/):\n" +
            "\n" +
            "    his approach has the advantage of nicely taking care of the thundering herds problem where many requests to a\n" +
            "    particular cache key (e.g. a resource URI) arrive before the first one could be completed. Normally\n" +
            "    (without special guarding techniques, like so-called “cowboy entries) this can cause many requests\n" +
            "    to compete for system resources while trying to compute the same result thereby greatly reducing overall\n" +
            "    system performance. When you use a spray-caching cache the very first request that arrives for a certain\n" +
            "    cache key causes a future to be put into the cache which all later requests then hook into. As soon as\n" +
            "    the first request completes all other ones complete as well. This minimizes processing time and server\n" +
            "    load for all requests.\n" +
            "\n" +
            "Basically if many requests come in for the same key, and the value has not yet been computed.  Rather than each\n" +
            "request compute the value, all the requests wait on the 1 invocation of the value to be computed.\n" +
            "\n" +
            "### Memcached thundering herd, how does it do it  ###\n" +
            "\n" +
            "There is really no real difference between the SimpleLruCache (https://github.com/spray/spray/blob/v1.2.1/spray-caching/src/main/scala/spray/caching/LruCache.scala#L54)\n" +
            "and the memcached library implementation.\n" +
            "\n" +
            "\n" +
            "# Memcached For Spray #\n" +
            "\n" +
            "This library is an extension of the spray-caching (http://spray.io/documentation/1.2.1/spray-caching/).  It provides\n" +
            "a memcached backed cache for spray caching.\n" +
            "\n" +
            "## Overview ##\n" +
            "\n" +
            "Uses an internal ConcurrentLinkedHashMap (https://code.google.com/p/concurrentlinkedhashmap/) to provide a storage of\n" +
            "keys to executing futures.\n" +
            "\n" +
            "When the future completes the computed value (which must be a serializable object), is asynchronously stored in memcached.\n" +
            "At this point the entry is removed from the internal cache; and will only exist in memcached.\n" +
            "\n" +
            "Before the value of the future is computed, memcached is checked for a value.  If a pre-existing value is found this is\n" +
            "returned.\n" +
            "\n" +
            "The keys for the cache must have a toString method that represents that object.  Memcached requires string keys, and serialized\n" +
            "objects.\n" +
            "\n" +
            "\n" +
            "## Dependencies ##\n" +
            "\n" +
            "The library uses the Java Spy Memcached library (https://code.google.com/p/spymemcached/), to communicate with memcached.\n" +
            "\n" +
            "## Thundering Herd ##\n" +
            "\n" +
            "The spray caching api, isn't quite suited for distributed caching implementations.  The existing LRU (Simple and Expiring),\n" +
            "store in the cache a future.  As a result you can't really store a future in a distributed cache.  As the future may or may\n" +
            "not be completed, and is specific to the client that generated the future.\n" +
            "\n" +
            "This architecture lend it's self nicely to the thundering herd issue (as detailed on http://spray.io/documentation/1.2.1/spray-caching/):\n" +
            "\n" +
            "    his approach has the advantage of nicely taking care of the thundering herds problem where many requests to a\n" +
            "    particular cache key (e.g. a resource URI) arrive before the first one could be completed. Normally\n" +
            "    (without special guarding techniques, like so-called \"cowboy\" entries) this can cause many requests\n" +
            "    to compete for system resources while trying to compute the same result thereby greatly reducing overall\n" +
            "    system performance. When you use a spray-caching cache the very first request that arrives for a certain\n" +
            "    cache key causes a future to be put into the cache which all later requests then “hook into. As soon as\n" +
            "    the first request completes all other ones complete as well. This minimizes processing time and server\n" +
            "    load for all requests.\n" +
            "\n" +
            "Basically if many requests come in for the same key, and the value has not yet been computed.  Rather than each\n" +
            "request compute the value, all the requests wait on the 1 invocation of the value to be computed.\n" +
            "\n" +
            "### Memcached thundering herd, how does it do it  ###\n" +
            "\n" +
            "There is really no real difference between the SimpleLruCache (https://github.com/spray/spray/blob/v1.2.1/spray-caching/src/main/scala/spray/caching/LruCache.scala#L54)\n" +
            "and the memcached library implementation.\n" +
            "\n" +
            "\n" +
            "# Memcached For Spray #\n" +
            "\n" +
            "This library is an extension of the spray-caching (http://spray.io/documentation/1.2.1/spray-caching/).  It provides\n" +
            "a memcached backed cache for spray caching.\n" +
            "\n" +
            "## Overview ##\n" +
            "\n" +
            "Uses an internal ConcurrentLinkedHashMap (https://code.google.com/p/concurrentlinkedhashmap/) to provide a storage of\n" +
            "keys to executing futures.\n" +
            "\n" +
            "When the future completes the computed value (which must be a serializable object), is asynchronously stored in memcached.\n" +
            "At this point the entry is removed from the internal cache; and will only exist in memcached.\n" +
            "\n" +
            "Before the value of the future is computed, memcached is checked for a value.  If a pre-existing value is found this is\n" +
            "returned.\n" +
            "\n" +
            "The keys for the cache must have a toString method that represents that object.  Memcached requires string keys, and serialized\n" +
            "objects.\n" +
            "\n" +
            "\n" +
            "## Dependencies ##\n" +
            "\n" +
            "The library uses the Java Spy Memcached library (https://code.google.com/p/spymemcached/), to communicate with memcached.\n" +
            "\n" +
            "## Thundering Herd ##\n" +
            "\n" +
            "The spray caching api, isn't quite suited for distributed caching implementations.  The existing LRU (Simple and Expiring),\n" +
            "store in the cache a future.  As a result you can't really store a future in a distributed cache.  As the future may or may\n" +
            "not be completed, and is specific to the client that generated the future.\n" +
            "\n" +
            "This architecture lend it's self nicely to the thundering herd issue (as detailed on http://spray.io/documentation/1.2.1/spray-caching/):\n" +
            "\n" +
            "    his approach has the advantage of nicely taking care of the thundering herds problem where many requests to a\n" +
            "    particular cache key (e.g. a resource URI) arrive before the first one could be completed. Normally\n" +
            "    (without special guarding techniques, like so-called \"cowboy\" entries) this can cause many requests\n" +
            "    to compete for system resources while trying to compute the same result thereby greatly reducing overall\n" +
            "    system performance. When you use a spray-caching cache the very first request that arrives for a certain\n" +
            "    cache key causes a future to be put into the cache which all later requests then “hook into. As soon as\n" +
            "    the first request completes all other ones complete as well. This minimizes processing time and server\n" +
            "    load for all requests.\n" +
            "\n" +
            "Basically if many requests come in for the same key, and the value has not yet been computed.  Rather than each\n" +
            "request compute the value, all the requests wait on the 1 invocation of the value to be computed.\n" +
            "\n" +
            "### Memcached thundering herd, how does it do it  ###\n" +
            "\n" +
            "There is really no real difference between the SimpleLruCache (https://github.com/spray/spray/blob/v1.2.1/spray-caching/src/main/scala/spray/caching/LruCache.scala#L54)\n" +
            "and the memcached library implementation.\n" +
            "\n" +
            "\n" +
            "# Memcached For Spray #\n" +
            "\n" +
            "This library is an extension of the spray-caching (http://spray.io/documentation/1.2.1/spray-caching/).  It provides\n" +
            "a memcached backed cache for spray caching.\n" +
            "\n" +
            "## Overview ##\n" +
            "\n" +
            "Uses an internal ConcurrentLinkedHashMap (https://code.google.com/p/concurrentlinkedhashmap/) to provide a storage of\n" +
            "keys to executing futures.\n" +
            "\n" +
            "When the future completes the computed value (which must be a serializable object), is asynchronously stored in memcached.\n" +
            "At this point the entry is removed from the internal cache; and will only exist in memcached.\n" +
            "\n" +
            "Before the value of the future is computed, memcached is checked for a value.  If a pre-existing value is found this is\n" +
            "returned.\n" +
            "\n" +
            "The keys for the cache must have a toString method that represents that object.  Memcached requires string keys, and serialized\n" +
            "objects.\n" +
            "\n" +
            "\n" +
            "## Dependencies ##\n" +
            "\n" +
            "The library uses the Java Spy Memcached library (https://code.google.com/p/spymemcached/), to communicate with memcached.\n" +
            "\n" +
            "## Thundering Herd ##\n" +
            "\n" +
            "The spray caching api, isn't quite suited for distributed caching implementations.  The existing LRU (Simple and Expiring),\n" +
            "store in the cache a future.  As a result you can't really store a future in a distributed cache.  As the future may or may\n" +
            "not be completed, and is specific to the client that generated the future.\n" +
            "\n" +
            "This architecture lend it's self nicely to the thundering herd issue (as detailed on http://spray.io/documentation/1.2.1/spray-caching/):\n" +
            "\n" +
            "    his approach has the advantage of nicely taking care of the thundering herds problem where many requests to a\n" +
            "    particular cache key (e.g. a resource URI) arrive before the first one could be completed. Normally\n" +
            "    (without special guarding techniques, like so-called \"cowboy\" entries) this can cause many requests\n" +
            "    to compete for system resources while trying to compute the same result thereby greatly reducing overall\n" +
            "    system performance. When you use a spray-caching cache the very first request that arrives for a certain\n" +
            "    cache key causes a future to be put into the cache which all later requests then \"hook into\". As soon as\n" +
            "    the first request completes all other ones complete as well. This minimizes processing time and server\n" +
            "    load for all requests.\n" +
            "\n" +
            "Basically if many requests come in for the same key, and the value has not yet been computed.  Rather than each\n" +
            "request compute the value, all the requests wait on the 1 invocation of the value to be computed.\n" +
            "\n" +
            "### Memcached thundering herd, how does it do it  ###\n" +
            "\n" +
            "There is really no real difference between the SimpleLruCache (https://github.com/spray/spray/blob/v1.2.1/spray-caching/src/main/scala/spray/caching/LruCache.scala#L54)\n" +
            "and the memcached library implementation.\n" +
            "\n" +
            "\n" +
            "# Memcached For Spray #\n" +
            "\n" +
            "This library is an extension of the spray-caching (http://spray.io/documentation/1.2.1/spray-caching/).  It provides\n" +
            "a memcached backed cache for spray caching.\n" +
            "\n" +
            "## Overview ##\n" +
            "\n" +
            "Uses an internal ConcurrentLinkedHashMap (https://code.google.com/p/concurrentlinkedhashmap/) to provide a storage of\n" +
            "keys to executing futures.\n" +
            "\n" +
            "When the future completes the computed value (which must be a serializable object), is asynchronously stored in memcached.\n" +
            "At this point the entry is removed from the internal cache; and will only exist in memcached.\n" +
            "\n" +
            "Before the value of the future is computed, memcached is checked for a value.  If a pre-existing value is found this is\n" +
            "returned.\n" +
            "\n" +
            "The keys for the cache must have a toString method that represents that object.  Memcached requires string keys, and serialized\n" +
            "objects.\n" +
            "\n" +
            "\n" +
            "## Dependencies ##\n" +
            "\n" +
            "The library uses the Java Spy Memcached library (https://code.google.com/p/spymemcached/), to communicate with memcached.\n" +
            "\n" +
            "## Thundering Herd ##\n" +
            "\n" +
            "The spray caching api, isn't quite suited for distributed caching implementations.  The existing LRU (Simple and Expiring),\n" +
            "store in the cache a future.  As a result you can't really store a future in a distributed cache.  As the future may or may\n" +
            "not be completed, and is specific to the client that generated the future.\n" +
            "\n" +
            "This architecture lend it's self nicely to the thundering herd issue (as detailed on http://spray.io/documentation/1.2.1/spray-caching/):\n" +
            "\n" +
            "    his approach has the advantage of nicely taking care of the thundering herds problem where many requests to a\n" +
            "    particular cache key (e.g. a resource URI) arrive before the first one could be completed. Normally\n" +
            "    (without special guarding techniques, like so-called “cowboy entries) this can cause many requests\n" +
            "    to compete for system resources while trying to compute the same result thereby greatly reducing overall\n" +
            "    system performance. When you use a spray-caching cache the very first request that arrives for a certain\n" +
            "    cache key causes a future to be put into the cache which all later requests then \"hook into\". As soon as\n" +
            "    the first request completes all other ones complete as well. This minimizes processing time and server\n" +
            "    load for all requests.\n" +
            "\n" +
            "Basically if many requests come in for the same key, and the value has not yet been computed.  Rather than each\n" +
            "request compute the value, all the requests wait on the 1 invocation of the value to be computed.\n" +
            "\n" +
            "### Memcached thundering herd, how does it do it  ###\n" +
            "\n" +
            "There is really no real difference between the SimpleLruCache (https://github.com/spray/spray/blob/v1.2.1/spray-caching/src/main/scala/spray/caching/LruCache.scala#L54)\n" +
            "and the memcached library implementation.\n" +
            "\n" +
            "\n" +
            "# Memcached For Spray #\n" +
            "\n" +
            "This library is an extension of the spray-caching (http://spray.io/documentation/1.2.1/spray-caching/).  It provides\n" +
            "a memcached backed cache for spray caching.\n" +
            "\n" +
            "## Overview ##\n" +
            "\n" +
            "Uses an internal ConcurrentLinkedHashMap (https://code.google.com/p/concurrentlinkedhashmap/) to provide a storage of\n" +
            "keys to executing futures.\n" +
            "\n" +
            "When the future completes the computed value (which must be a serializable object), is asynchronously stored in memcached.\n" +
            "At this point the entry is removed from the internal cache; and will only exist in memcached.\n" +
            "\n" +
            "Before the value of the future is computed, memcached is checked for a value.  If a pre-existing value is found this is\n" +
            "returned.\n" +
            "\n" +
            "The keys for the cache must have a toString method that represents that object.  Memcached requires string keys, and serialized\n" +
            "objects.\n" +
            "\n" +
            "\n" +
            "## Dependencies ##\n" +
            "\n" +
            "The library uses the Java Spy Memcached library (https://code.google.com/p/spymemcached/), to communicate with memcached.\n" +
            "\n" +
            "## Thundering Herd ##\n" +
            "\n" +
            "The spray caching api, isn't quite suited for distributed caching implementations.  The existing LRU (Simple and Expiring),\n" +
            "store in the cache a future.  As a result you can't really store a future in a distributed cache.  As the future may or may\n" +
            "not be completed, and is specific to the client that generated the future.\n" +
            "\n" +
            "This architecture lend it's self nicely to the thundering herd issue (as detailed on http://spray.io/documentation/1.2.1/spray-caching/):\n" +
            "\n" +
            "    his approach has the advantage of nicely taking care of the thundering herds problem where many requests to a\n" +
            "    particular cache key (e.g. a resource URI) arrive before the first one could be completed. Normally\n" +
            "    (without special guarding techniques, like so-called \"cowboy\" entries) this can cause many requests\n" +
            "    to compete for system resources while trying to compute the same result thereby greatly reducing overall\n" +
            "    system performance. When you use a spray-caching cache the very first request that arrives for a certain\n" +
            "    cache key causes a future to be put into the cache which all later requests then “hook into. As soon as\n" +
            "    the first request completes all other ones complete as well. This minimizes processing time and server\n" +
            "    load for all requests.\n" +
            "\n" +
            "Basically if many requests come in for the same key, and the value has not yet been computed.  Rather than each\n" +
            "request compute the value, all the requests wait on the 1 invocation of the value to be computed.\n" +
            "\n" +
            "### Memcached thundering herd, how does it do it  ###\n" +
            "\n" +
            "There is really no real difference between the SimpleLruCache (https://github.com/spray/spray/blob/v1.2.1/spray-caching/src/main/scala/spray/caching/LruCache.scala#L54)\n" +
            "and the memcached library implementation.\n" +
            "\n"+ UUID.randomUUID().toString();

    private MemcachedDaemonWrapper memcached;
    private ListeningExecutorService executorService;
    private CacheWithExpiry cache;

    @Before
    public void setUp() {
        executorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(10));

        memcached = MemcachedDaemonFactory.createMemcachedDaemon(false);

        if(memcached.getDaemon()==null) {
            throw new RuntimeException("Unable to start local memcached");
        }


    }

    @After
    public void tearDown() {
        if(memcached!=null) {
            memcached.getDaemon().stop();
        }

        if(cache!=null && cache instanceof RequiresShutdown) {
            ((RequiresShutdown) cache).shutdown();
        }
    }

    private void testHashAlgorithm(HashAlgorithm algo) {
        cache = new MemcachedCache<String>(
                new ElastiCacheCacheConfigBuilder()
                        .setMemcachedHosts("localhost:" + memcached.getPort())
                        .setTimeToLive(Duration.ofSeconds(60))
                        .setProtocol(ConnectionFactoryBuilder.Protocol.TEXT)
                        .setWaitForMemcachedSet(true)
                        .setHashAlgorithm(algo)
                        .buildMemcachedConfig()
        );

        ListenableFuture<String> val = cache.apply("Key1", () -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return "value1";
        }, executorService);

        ListenableFuture<String> val2 = cache.apply("Key1", () -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return "value2";
        }, executorService);


        assertEquals("Value should be key1","value1",cache.awaitForFutureOrElse(val, null));
        assertEquals("Value should be key1","value1",cache.awaitForFutureOrElse(val2, null));

        assertEquals(1, memcached.getDaemon().getCache().getCurrentItems());

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

    @Test
    public void testMemcachedCache() {

        cache = new MemcachedCache<>(
                new ElastiCacheCacheConfigBuilder()
                        .setMemcachedHosts("localhost:" + memcached.getPort())
                        .setTimeToLive(Duration.ofSeconds(60))
                        .setProtocol(ConnectionFactoryBuilder.Protocol.TEXT)
                        .setWaitForMemcachedSet(true)
                        .buildMemcachedConfig()
        );

        ListenableFuture<String> val = cache.apply("Key1", () -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return "value1";
        }, executorService);

        ListenableFuture<String> val2 = cache.apply("Key1", () -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return "value2";
        }, executorService);


        assertEquals("Value should be key1","value1",cache.awaitForFutureOrElse(val, null));
        assertEquals("Value should be key1","value1",cache.awaitForFutureOrElse(val2, null));

        assertEquals(1, memcached.getDaemon().getCache().getCurrentItems());

    }

    @Test
    public void testNoCacheKeyHashingMemcachedCache() {
        cache = new MemcachedCache<>(
                new ElastiCacheCacheConfigBuilder()
                        .setMemcachedHosts("localhost:" + memcached.getPort())
                        .setTimeToLive(Duration.ofSeconds(60))
                        .setProtocol(ConnectionFactoryBuilder.Protocol.TEXT)
                        .setWaitForMemcachedSet(true)
                        .setKeyHashType(KeyHashingType.NONE)
                        .buildMemcachedConfig()
        );

        ListenableFuture<String> val = cache.apply("Key1", () -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return "value1";
        }, executorService);

        ListenableFuture<String> val2 = cache.apply("Key1", () -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return "value2";
        }, executorService);


        assertEquals("Value should be key1","value1",cache.awaitForFutureOrElse(val, null));
        assertEquals("Value should be key1","value1",cache.awaitForFutureOrElse(val2, null));

        assertEquals(1, memcached.getDaemon().getCache().getCurrentItems());

    }


    @Test
    public void testMD5LowerKeyHashingMemcachedCache() {
        cache = new MemcachedCache<>(
                new ElastiCacheCacheConfigBuilder()
                        .setMemcachedHosts("localhost:" + memcached.getPort())
                        .setTimeToLive(Duration.ofSeconds(60))
                        .setProtocol(ConnectionFactoryBuilder.Protocol.TEXT)
                        .setWaitForMemcachedSet(true)
                        .setKeyHashType(KeyHashingType.MD5_LOWER)
                        .buildMemcachedConfig()
        );

        ListenableFuture<String> val = cache.apply("Key1", () -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return "value1";
        }, executorService);

        ListenableFuture<String> val2 = cache.apply("Key1", () -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return "value2";
        }, executorService);


        assertEquals("Value should be key1","value1",cache.awaitForFutureOrElse(val, null));
        assertEquals("Value should be key1","value1",cache.awaitForFutureOrElse(val2, null));

        assertEquals(1, memcached.getDaemon().getCache().getCurrentItems());

    }

    @Test
    public void testSHA256LowerKeyHashingMemcachedCache() {
        cache = new MemcachedCache<String>(
                new ElastiCacheCacheConfigBuilder()
                        .setMemcachedHosts("localhost:" + memcached.getPort())
                        .setTimeToLive(Duration.ofSeconds(60))
                        .setProtocol(ConnectionFactoryBuilder.Protocol.TEXT)
                        .setWaitForMemcachedSet(true)
                        .setKeyHashType(KeyHashingType.SHA256_LOWER)
                        .buildMemcachedConfig()
        );

        ListenableFuture<String> val = cache.apply("Key1", () -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return "value1";
        }, executorService);

        ListenableFuture<String> val2 = cache.apply("Key1", () -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return "value2";
        }, executorService);


        assertEquals("Value should be key1","value1",cache.awaitForFutureOrElse(val, null));
        assertEquals("Value should be key1","value1",cache.awaitForFutureOrElse(val2, null));

        assertEquals(1, memcached.getDaemon().getCache().getCurrentItems());

    }



    @Test
    public void testMD5UpperKeyHashingMemcachedCache() {
        cache = new MemcachedCache<>(
                new ElastiCacheCacheConfigBuilder()
                        .setMemcachedHosts("localhost:" + memcached.getPort())
                        .setTimeToLive(Duration.ofSeconds(60))
                        .setProtocol(ConnectionFactoryBuilder.Protocol.TEXT)
                        .setWaitForMemcachedSet(true)
                        .setKeyHashType(KeyHashingType.MD5_UPPER)
                        .buildMemcachedConfig()
        );

        ListenableFuture<String> val = cache.apply("Key1", () -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return "value1";
        }, executorService);

        ListenableFuture<String> val2 = cache.apply("Key1", () -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return "value2";
        }, executorService);


        assertEquals("Value should be key1","value1",cache.awaitForFutureOrElse(val, null));
        assertEquals("Value should be key1","value1",cache.awaitForFutureOrElse(val2, null));

        assertEquals(1, memcached.getDaemon().getCache().getCurrentItems());

    }

    public static class Document implements Serializable{
        static final long serialVersionUID = 42L;

        private final String title;
        private final String author;
        private final String content;

        public Document(String title,String author, String content) {
            this.title = title;
            this.author = author;
            this.content = content;
        }

        public boolean equals(Object o) {
            if(o instanceof Document) {
                Document other = (Document)o;

                if(other.title.equals(this.title) &&
                        other.author.equals(this.author) &&
                        other.content.equals(this.content)) {
                    return true;
                } else {
                    return false;
                }
            }
            else {
                return false;
            }
        }
    }

    @Test
    public void testSerializationInMemcachedCache() {
        cache = new MemcachedCache<Document>(
                new ElastiCacheCacheConfigBuilder()
                        .setMemcachedHosts("localhost:" + memcached.getPort())
                        .setTimeToLive(Duration.ofSeconds(60))
                        .setProtocol(ConnectionFactoryBuilder.Protocol.TEXT)
                        .setWaitForMemcachedSet(true)
                        .setKeyHashType(KeyHashingType.MD5_UPPER)
                        .setSerializingTranscoder(new SerializingTranscoder())
                        .buildMemcachedConfig()
        );

        Document nemo = new Document("Finding Nemo","Disney",largeCacheValue);
        Document jungle = new Document("Jungle Book","Disney",largeCacheValue);
        ListenableFuture<String> val = cache.apply("Key1", () -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return nemo;
        }, executorService);

        ListenableFuture<String> val2 = cache.apply("Key1", () -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return jungle;
        }, executorService);


        assertEquals("Value should be key1",nemo,cache.awaitForFutureOrElse(val, null));
        assertEquals("Value should be key1",nemo,cache.awaitForFutureOrElse(val2, null));

        assertEquals(1, memcached.getDaemon().getCache().getCurrentItems());

    }

    @Test
    public void testFastSerializationInMemcachedCache() {
        cache = new MemcachedCache<Document>(
                new ElastiCacheCacheConfigBuilder()
                        .setMemcachedHosts("localhost:" + memcached.getPort())
                        .setTimeToLive(Duration.ofSeconds(60))
                        .setProtocol(ConnectionFactoryBuilder.Protocol.TEXT)
                        .setWaitForMemcachedSet(true)
                        .setKeyHashType(KeyHashingType.MD5_UPPER)
                        .setSerializingTranscoder(new FastSerializingTranscoder())
                        .buildMemcachedConfig()
        );

        Document nemo = new Document("Finding Nemo","Disney",largeCacheValue);
        Document jungle = new Document("Jungle Book","Disney",largeCacheValue);
        ListenableFuture<String> val = cache.apply("Key1", () -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return nemo;
        }, executorService);

        ListenableFuture<String> val2 = cache.apply("Key1", () -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return jungle;
        }, executorService);


        assertEquals("Value should be nemo object",nemo,cache.awaitForFutureOrElse(val, null));
        assertEquals("Value should be nemo object",nemo,cache.awaitForFutureOrElse(val2, null));

        assertEquals("Value should be nemo object",nemo,cache.awaitForFutureOrElse(cache.get("Key1"), null));

        assertEquals(1, memcached.getDaemon().getCache().getCurrentItems());

    }

    @Test
    public void testLargeCacheByteValue() {
        byte[] largeCacheValueAsBytes = largeCacheValue.getBytes();

        cache = new MemcachedCache<byte[]>(
                new ElastiCacheCacheConfigBuilder()
                        .setMemcachedHosts("localhost:" + memcached.getPort())
                        .setTimeToLive(Duration.ofSeconds(60))
                        .setProtocol(ConnectionFactoryBuilder.Protocol.TEXT)
                        .setWaitForMemcachedSet(true)
                        .setKeyHashType(KeyHashingType.MD5_LOWER)
                        .setSerializingTranscoder(new SerializingTranscoder(Integer.MAX_VALUE))
                        .buildMemcachedConfig()
        );

        ListenableFuture<String> val = cache.apply("Key1", () -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return largeCacheValueAsBytes ;
        }, executorService);

        ListenableFuture<String> val2 = cache.apply("Key1", () -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return "value2".getBytes();
        }, executorService);


        assertEquals("Value should be key1",largeCacheValueAsBytes,cache.awaitForFutureOrElse(val, null));
        assertEquals("Value should be key1",largeCacheValueAsBytes,cache.awaitForFutureOrElse(val2, null));

        assertEquals(1, memcached.getDaemon().getCache().getCurrentItems());

    }


    @Test
    public void testLargeCacheValue() {



        cache = new MemcachedCache<>(
                new ElastiCacheCacheConfigBuilder()
                        .setMemcachedHosts("localhost:" + memcached.getPort())
                        .setTimeToLive(Duration.ofSeconds(60))
                        .setProtocol(ConnectionFactoryBuilder.Protocol.TEXT)
                        .setWaitForMemcachedSet(true)
                        .setKeyHashType(KeyHashingType.MD5_LOWER)
                        .buildMemcachedConfig()
        );

        ListenableFuture<String> val = cache.apply("Key1", () -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return largeCacheValue ;
        }, executorService);

        ListenableFuture<String> val2 = cache.apply("Key1", () -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return "value2";
        }, executorService);


        assertEquals("Value should be key1",largeCacheValue,cache.awaitForFutureOrElse(val, null));
        assertEquals("Value should be key1",largeCacheValue,cache.awaitForFutureOrElse(val2, null));

        assertEquals(1, memcached.getDaemon().getCache().getCurrentItems());

    }

    @Test
    public void testSHA256UpperKeyHashingMemcachedCache() {
        cache = new MemcachedCache<>(
                new ElastiCacheCacheConfigBuilder()
                        .setMemcachedHosts("localhost:" + memcached.getPort())
                        .setTimeToLive(Duration.ofSeconds(60))
                        .setProtocol(ConnectionFactoryBuilder.Protocol.TEXT)
                        .setWaitForMemcachedSet(true)
                        .setKeyHashType(KeyHashingType.SHA256_UPPER)
                        .buildMemcachedConfig()
        );

        ListenableFuture<String> val = cache.apply("Key1", () -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return "value1";
        }, executorService);

        ListenableFuture<String> val2 = cache.apply("Key1", () -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return "value2";
        }, executorService);


        assertEquals("Value should be key1","value1",cache.awaitForFutureOrElse(val, null));
        assertEquals("Value should be key1","value1",cache.awaitForFutureOrElse(val2, null));

        assertEquals(1, memcached.getDaemon().getCache().getCurrentItems());

    }
}
