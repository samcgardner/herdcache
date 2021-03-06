package org.greencheek.caching.herdcache.domain;

import java.io.Serializable;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Wraps a user supplied cache value.  It is the CachedItemWithCreationDate object that is
 * then stored in the cache (i.e. memcached).  The CachedItemWithCreationDate has one extra
 * instance member, that of a {@link java.time.Instant} that represents the time the
 * CachedItemWithCreationDate was created ({@link #getCreationDate()}.
 * The instant is recorded in UTC ({@link java.time.Clock#systemUTC()}).
 */
public class CacheableItemWithCreationDate<V extends Serializable> implements Serializable {

    /**
     * Serialization version.
     */
    private static final long serialVersionUID = -665713676816604377L;
    private static Clock UTC = Clock.systemUTC();

    private final V cachedItem;
    private final Instant creationDate;

    /**
     * Boolean to mark if this object was generated as a
     * Default Item, in response to an error condition
     */
    private final boolean fallback;


    /**
     * When returning a fallback item, should this indicate that
     * we check a cache for an overriding item, or use this fallback item.
     * @param item
     */
    private final boolean checkForCachedItemWhenFallback;

    public CacheableItemWithCreationDate(V item) {
        this(item,false,false);
    }

    public CacheableItemWithCreationDate(V item, boolean fallback) {
        this(item,fallback, fallback==true ? true : false);
    }
    public CacheableItemWithCreationDate(V item, boolean fallback,boolean checkForCachedItem) {
        this.cachedItem = item;
        creationDate = Instant.now(UTC);
        this.fallback = fallback;
        if(!fallback) {
            this.checkForCachedItemWhenFallback = true;
        } else {
            this.checkForCachedItemWhenFallback = checkForCachedItem;
        }
    }

    /**
     * Is this instance actually a generic object that represents a failure condition, and
     * as a result contains a generic piece of content that will be returned by {@link #getCachedItem()}
     * @return
     */
    public boolean isFallback() {
        return fallback;
    }

    public boolean shouldCheckForCachedItemWhenFallback() {
        return checkForCachedItemWhenFallback;
    }

    public V getCachedItem() {
        return cachedItem;
    }

    /**
     * Given a time in millis, checks if the CacheItem's age is less that the given millis old.
     * The millis is basically added to the time when the
     * cached item was created and compares it against current time (in UTC)
     *
     * Returns true if the item is still less than given ttl in millis (i.e. is still alive)
     *
     * @param ttlInMillis
     * @return
     */
    public boolean isLive(long ttlInMillis) {
        Instant now = Instant.now(UTC);
        return creationDate.plusMillis(ttlInMillis).isAfter(now);
    }

    /**
     * Given a duration, this is converted to millis and {@link #isLive(long)} is called
     * @param ttl
     * @return
     */
    public boolean isLive(Duration ttl) {
        return isLive(ttl.toMillis());
    }

    /**
     * Returns true if the age of the CachedItem is more than the given ttlInMillis. i.e. The item is Expired
     * @param ttlInMillis
     * @return
     */
    public boolean hasExpired(long ttlInMillis) {
        return !isLive(ttlInMillis);
    }

    /**
     * Returns true if the age of the CachedItem is more than the given Duration. i.e. The item is Expired
     * @param ttl
     * @return
     */
    public boolean hasExpired(Duration ttl) {
        return hasExpired(ttl.toMillis());
    }

    public Instant getCreationDate() {
        return creationDate;
    }
}